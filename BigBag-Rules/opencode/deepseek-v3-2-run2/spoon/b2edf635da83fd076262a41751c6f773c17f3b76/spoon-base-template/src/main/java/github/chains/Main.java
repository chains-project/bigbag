package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic, reusable transformation rule for the breaking dependency update introduced by
 * org.yaml:snakeyaml 2.0 in the "constructor family" of the library.
 *
 * <p>Abstract characterisation of the breaking change:
 * <pre>
 *   old API pattern : new &lt;CtorType&gt;(p0, ..., pn)
 *   new API pattern : new &lt;CtorType&gt;(p0, ..., pn, new &lt;RequiredType&gt;())
 * </pre>
 * Every constructor of the affected dependency types lost its configuration-free overload and now
 * mandates one additional parameter of type {@link #REQUIRED_ARGUMENT_TYPE}, which is default
 * constructible. The structural transformation imposed on each call site is therefore exactly:
 * "insert a default-constructed instance of the required parameter type into the argument list,
 * unless an argument of that type is already present".
 *
 * <p>The rule holds no client identifier: it is parameterised only by fully-qualified type names
 * belonging to the dependency (the constants below) and it traverses every Java file found under
 * the given input path, so any other Maven/Java project hit by the same dependency bump can be
 * repaired by changing only the input source directory.
 *
 * <p>Matching is structural (Spoon model), while the rewrite is a minimal text insertion driven by
 * source positions, so files keep their original formatting, license headers and imports.
 *
 * <p>Usage:
 * <pre>java github.chains.Main &lt;file|source-dir|project-dir&gt; [-d &lt;output-dir&gt;] [-cp &lt;classpath&gt;] [--compliance &lt;n&gt;]</pre>
 */
public class Main {

    /** Root package of the dependency; used when the analysed model cannot resolve its types. */
    private static final String DEPENDENCY_PACKAGE = "org.yaml.snakeyaml";

    /** Fully-qualified dependency types whose constructors gained the extra mandatory parameter. */
    private static final Set<String> AFFECTED_TYPES = new LinkedHashSet<>(Arrays.asList(
            DEPENDENCY_PACKAGE + ".constructor.BaseConstructor",
            DEPENDENCY_PACKAGE + ".constructor.SafeConstructor",
            DEPENDENCY_PACKAGE + ".constructor.Constructor",
            DEPENDENCY_PACKAGE + ".constructor.CustomClassLoaderConstructor"));

    /** Fully-qualified dependency type of the newly mandatory parameter (default constructible). */
    private static final String REQUIRED_ARGUMENT_TYPE = DEPENDENCY_PACKAGE + ".LoaderOptions";

    /** Where the new argument is inserted; negative (or >= arity) means "append at the end". */
    private static final int INSERTION_INDEX = -1;

    /** Also repair {@code super(...)} calls of client subclasses that extend an affected type. */
    private static final boolean FIX_SUBCLASS_SUPER_CALLS = true;

    /** Written text of the inserted argument; fully qualified so that no import is required. */
    private static final String FILLER_EXPRESSION = "new " + REQUIRED_ARGUMENT_TYPE + "()";

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: Main <source-dir> [-d <output-dir>] [-cp <classpath>] [--compliance <n>]");
            System.exit(2);
        }
        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        List<String> sources = resolveInputSources(input);
        File outputDir = null;
        List<String> classpath = new ArrayList<>();
        int compliance = 11;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-d":
                    outputDir = new File(args[++i]);
                    break;
                case "-cp":
                    classpath.addAll(splitClasspath(args[++i]));
                    break;
                case "--compliance":
                    compliance = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("ignoring unknown argument: " + args[i]);
            }
        }

        Launcher launcher = new Launcher();
        for (String source : sources) {
            launcher.addInputResource(source);
        }
        if (!classpath.isEmpty()) {
            launcher.getEnvironment().setSourceClasspath(classpath.toArray(new String[0]));
        }
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(compliance);

        launcher.buildModel();
        Map<File, List<Patch>> patches = collectPatches(launcher.getModel());

        if (patches.isEmpty()) {
            System.out.println("No occurrence of the old API pattern found; nothing was changed.");
            return;
        }

        int applied = 0;
        for (Map.Entry<File, List<Patch>> entry : patches.entrySet()) {
            File file = entry.getKey();
            String content = new String(Files.readAllBytes(file.toPath()), CHARSET);
            String updated = apply(content, entry.getValue(), file);
            if (updated.equals(content)) {
                continue;
            }
            Path target = (outputDir == null) ? file.toPath() : outputTarget(outputDir.toPath(), file, sources);
            Files.createDirectories(target.getParent());
            Files.write(target, updated.getBytes(CHARSET));
            applied += entry.getValue().size();
            System.out.println("patched " + file + " (" + entry.getValue().size() + " call site(s))");
        }
        System.out.println("Applied the rule to " + applied + " call site(s).");
    }

    /** Finds every call site that still uses the old API pattern. */
    private static Map<File, List<Patch>> collectPatches(CtModel model) {
        Map<File, List<Patch>> result = new LinkedHashMap<>();

        // Pass 1 - explicit instantiation of an affected dependency type: new <CtorType>(...)
        for (CtConstructorCall constructorCall : model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class))) {
            if (isAffected(constructorCall.getType())) {
                addPatch(result, constructorCall, "new", simpleNameOf(constructorCall.getType()));
            }
        }

        // Pass 2 - super(...) calls inside client subclasses of an affected dependency type
        if (FIX_SUBCLASS_SUPER_CALLS) {
            for (CtType<?> type : model.getAllTypes()) {
                if (!(type instanceof CtClass) || !isAffected(type.getSuperclass())) {
                    continue;
                }
                for (CtConstructor<?> constructor : ((CtClass<?>) type).getConstructors()) {
                    for (CtInvocation invocation : constructor.getElements(new TypeFilter<CtInvocation>(CtInvocation.class))) {
                        CtExecutableReference<?> executable = invocation.getExecutable();
                        boolean superCall = executable != null && "<init>".equals(executable.getSimpleName())
                                && (invocation.getTarget() == null || invocation.getTarget() instanceof CtSuperAccess);
                        if (superCall) {
                            addPatch(result, invocation, "super", "");
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void addPatch(Map<File, List<Patch>> result, CtAbstractInvocation invocation, String keyword, String typeName) {
        if (!needsRequiredArgument(invocation.getArguments())) {
            return; // already migrated - keeps the rule idempotent
        }
        CtElement element = invocation;
        if (element.getPosition() == null || !element.getPosition().isValidPosition()
                || element.getPosition().getFile() == null) {
            System.err.println("warning: matching call without source position; skipped");
            return;
        }
        Patch patch = new Patch(element.getPosition().getFile(), element.getPosition().getLine(),
                element.getPosition().getColumn(), keyword, typeName,
                invocation.getArguments().size(), INSERTION_INDEX);
        result.computeIfAbsent(patch.file, f -> new ArrayList<>()).add(patch);
    }

    /** Applies every patch of one file, last match first so that earlier offsets stay valid. */
    private static String apply(String content, List<Patch> patches, File file) {
        List<Patch> ordered = new ArrayList<>(patches);
        ordered.sort((a, b) -> (a.line != b.line) ? Integer.compare(b.line, a.line) : Integer.compare(b.column, a.column));
        int[] lineOffsets = lineOffsets(content);
        StringBuilder builder = new StringBuilder(content);
        for (Patch patch : ordered) {
            int open = findOpenParen(content, lineOffsets, patch);
            if (open < 0) {
                System.err.println("warning: could not locate the argument list of " + patch.keyword + " "
                        + patch.typeName + " at " + file + ":" + patch.line + ":" + patch.column);
                continue;
            }
            int close = matchingCloseParen(content, open);
            if (close < 0) {
                System.err.println("warning: unbalanced parentheses at " + file + ":" + patch.line);
                continue;
            }
            int argCount = countTopLevelArguments(content, open, close);
            int insertion;
            String text;
            if (patch.index < 0 || patch.index >= argCount) {
                // append at the end of the argument list, as mandated by the new signature
                insertion = close;
                text = (argCount == 0) ? FILLER_EXPRESSION : ", " + FILLER_EXPRESSION;
            } else if (patch.index == 0) {
                insertion = startOfArgument(content, open, close, 0);
                text = FILLER_EXPRESSION + ", ";
            } else {
                insertion = startOfArgument(content, open, close, patch.index);
                text = FILLER_EXPRESSION + ", ";
            }
            builder.insert(insertion, text);
        }
        return builder.toString();
    }

    /** Index of the '(' opening the argument list of the matched call. */
    private static int findOpenParen(String content, int[] lineOffsets, Patch patch) {
        int from = lineStart(lineOffsets, patch.line);
        int to = Math.min(lineEnd(content, lineOffsets, patch.line), content.length());
        int preferred = clamp(from + Math.max(0, patch.column - 1), from, to);
        int keyword = indexOfKeyword(content, from, to, patch, preferred);
        if (keyword < 0) {
            return -1;
        }
        int cursor = keyword + patch.keyword.length();
        cursor = skipWhitespace(content, cursor);
        if (!patch.typeName.isEmpty()) {
            while (cursor < content.length() && (Character.isLetterOrDigit(content.charAt(cursor)) || content.charAt(cursor) == '.')) {
                cursor++;
            }
            if (cursor < content.length() && content.charAt(cursor) == '<') {
                cursor = skipBalanced(content, cursor, '<', '>');
            }
        }
        cursor = skipWhitespace(content, cursor);
        return (cursor < content.length() && content.charAt(cursor) == '(') ? cursor : -1;
    }

    /** Occurrence of the API keyword introducing the call, closest to the reported position. */
    private static int indexOfKeyword(String content, int from, int to, Patch patch, int preferred) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        int cursor = from;
        while (cursor < to) {
            int found = content.indexOf(patch.keyword, cursor);
            if (found < 0 || found >= to) {
                break;
            }
            int after = found + patch.keyword.length();
            cursor = after;
            boolean boundaryBefore = found == 0 || !isIdentifierPart(content.charAt(found - 1));
            boolean boundaryAfter = after >= content.length() || !isIdentifierPart(content.charAt(after));
            int name = skipWhile(content, after, Character::isWhitespace);
            boolean typeMatches = patch.typeName.isEmpty() || content.startsWith(patch.typeName, name);
            if (boundaryBefore && boundaryAfter && typeMatches && Math.abs(found - preferred) < bestDistance) {
                best = found;
                bestDistance = Math.abs(found - preferred);
            }
        }
        return best;
    }

    /** Index of the ')' closing the argument list opened at {@code open} (literal and comment aware). */
    private static int matchingCloseParen(String content, int open) {
        int depth = 0;
        for (int i = open; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(content, i);
            } else if (c == '/' && i + 1 < content.length()
                    && (content.charAt(i + 1) == '/' || content.charAt(i + 1) == '*')) {
                i = skipComment(content, i);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int countTopLevelArguments(String content, int open, int close) {
        if (content.substring(open + 1, close).trim().isEmpty()) {
            return 0;
        }
        return 1 + topLevelCommas(content, open, close).size();
    }

    /** Offset of the first character of the given top-level argument. */
    private static int startOfArgument(String content, int open, int close, int index) {
        if (index == 0) {
            return skipWhile(content, open + 1, Character::isWhitespace);
        }
        List<Integer> commas = topLevelCommas(content, open, close);
        int anchor = (index - 1 < commas.size()) ? commas.get(index - 1) : open;
        return skipWhile(content, anchor + 1, Character::isWhitespace);
    }

    private static List<Integer> topLevelCommas(String content, int open, int close) {
        List<Integer> commas = new ArrayList<>();
        int depth = 0;
        for (int i = open + 1; i < close; i++) {
            char c = content.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(content, i);
            } else if (c == '/' && i + 1 < content.length()
                    && (content.charAt(i + 1) == '/' || content.charAt(i + 1) == '*')) {
                i = skipComment(content, i);
            } else if (c == '(' || c == '[' || c == '{' || c == '<') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}' || c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                commas.add(i);
            }
        }
        return commas;
    }

    private static int skipLiteral(String content, int start) {
        char quote = content.charAt(start);
        for (int i = start + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return content.length() - 1;
    }

    private static int skipComment(String content, int start) {
        if (content.charAt(start + 1) == '/') {
            int end = content.indexOf('\n', start);
            return (end < 0) ? content.length() - 1 : end - 1;
        }
        int end = content.indexOf("*/", start + 2);
        return (end < 0) ? content.length() - 1 : end + 1;
    }

    private static int skipBalanced(String content, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            if (content.charAt(i) == open) {
                depth++;
            } else if (content.charAt(i) == close && --depth == 0) {
                return i + 1;
            }
        }
        return start;
    }

    private static int skipWhile(String content, int cursor, Predicate<Character> predicate) {
        while (cursor < content.length() && predicate.test(content.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int skipWhitespace(String content, int cursor) {
        return skipWhile(content, cursor, Character::isWhitespace);
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int[] lineOffsets(String content) {
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                offsets.add(i + 1);
            }
        }
        int[] result = new int[offsets.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = offsets.get(i);
        }
        return result;
    }

    private static int lineStart(int[] lineOffsets, int line) {
        return lineOffsets[Math.min(Math.max(line, 1), lineOffsets.length) - 1];
    }

    private static int lineEnd(String content, int[] lineOffsets, int line) {
        int end = content.indexOf('\n', lineStart(lineOffsets, line));
        return (end < 0) ? content.length() : end;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }

    /** True when no argument already provides (or can be assigned to) the newly mandatory type. */
    private static boolean needsRequiredArgument(List<?> arguments) {
        for (Object element : arguments) {
            CtExpression<?> argument = (element instanceof CtExpression) ? (CtExpression<?>) element : null;
            CtTypeReference<?> argumentType = (argument == null) ? null : argument.getType();
            if (argumentType != null && isRequiredType(argumentType)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAffected(CtTypeReference<?> reference) {
        if (reference == null) {
            return false;
        }
        return matchesAny(AFFECTED_TYPES, reference)
                || ancestors(declarationOf(reference)).stream().anyMatch(a -> matchesAny(AFFECTED_TYPES, a));
    }

    private static boolean isRequiredType(CtTypeReference<?> reference) {
        return matchesAny(Collections.singleton(REQUIRED_ARGUMENT_TYPE), reference)
                || ancestors(declarationOf(reference)).stream().anyMatch(a -> matchesAny(Collections.singleton(REQUIRED_ARGUMENT_TYPE), a));
    }

    /** Compares a reference with dependency types, also accepting unqualified references. */
    private static boolean matchesAny(Set<String> dependencyTypes, CtTypeReference<?> reference) {
        String name = reference.getQualifiedName();
        for (String dependencyType : dependencyTypes) {
            if (dependencyType.equals(name)) {
                return true;
            }
            String simple = simpleName(dependencyType);
            if (simple.equals(name)
                    || (name.indexOf('.') >= 0 && name.startsWith(DEPENDENCY_PACKAGE) && simple.equals(simpleName(name)))) {
                return true;
            }
        }
        return false;
    }

    private static CtType<?> declarationOf(CtTypeReference<?> reference) {
        try {
            return reference.getDeclaration();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<CtTypeReference<?>> ancestors(CtType<?> type) {
        List<CtTypeReference<?>> result = new ArrayList<>();
        if (type == null) {
            return result;
        }
        try {
            result.addAll(type.getSuperInterfaces());
            CtTypeReference<?> superClass = type.getSuperclass();
            while (superClass != null) {
                result.add(superClass);
                CtType<?> resolved = declarationOf(superClass);
                superClass = (resolved == null) ? null : resolved.getSuperclass();
            }
        } catch (RuntimeException e) {
            // partially resolvable model: the supertypes collected so far are enough
        }
        return result;
    }

    private static String simpleNameOf(CtTypeReference<?> reference) {
        return (reference == null) ? "" : simpleName(reference.getQualifiedName());
    }

    private static String simpleName(String qualifiedName) {
        int idx = qualifiedName.lastIndexOf('.');
        return idx < 0 ? qualifiedName : qualifiedName.substring(idx + 1);
    }

    /** Accepts a file, a source folder, a Maven module or a whole project. */
    private static List<String> resolveInputSources(Path input) throws IOException {
        if (Files.isRegularFile(input)) {
            return Collections.singletonList(input.toString());
        }
        List<String> sourceRoots;
        try (Stream<Path> walk = Files.walk(input)) {
            sourceRoots = walk.filter(Files::isDirectory)
                    .filter(p -> p.endsWith(Paths.get("src", "main", "java")) || p.endsWith(Paths.get("src", "test", "java")))
                    .map(p -> p.toString()).sorted().collect(Collectors.toList());
        }
        if (sourceRoots.isEmpty()) {
            sourceRoots = Collections.singletonList(input.toString());
        }
        return sourceRoots;
    }

    /** Mirrors the input layout under the requested output directory. */
    private static Path outputTarget(Path outputDir, File file, List<String> sources) {
        Path source = file.toPath();
        Optional<String> root = sources.stream().map(Paths::get).filter(source::startsWith)
                .sorted((a, b) -> Integer.compare(b.toString().length(), a.toString().length()))
                .findFirst().map(Path::toString);
        Path relative = root.map(r -> Paths.get(r).relativize(source)).orElseGet(source::getFileName);
        return outputDir.resolve(relative);
    }

    private static List<String> splitClasspath(String classpath) {
        return Arrays.stream(classpath.split(File.pathSeparator))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /** One matched call site, described only by its position and arity in the original source. */
    private static final class Patch {
        private final File file;
        private final int line;
        private final int column;
        private final String keyword;
        private final String typeName;
        private final int argumentCount;
        private final int index;

        private Patch(File file, int line, int column, String keyword, String typeName, int argumentCount, int index) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.keyword = keyword;
            this.typeName = typeName;
            this.argumentCount = argumentCount;
            this.index = index;
        }
    }
}
