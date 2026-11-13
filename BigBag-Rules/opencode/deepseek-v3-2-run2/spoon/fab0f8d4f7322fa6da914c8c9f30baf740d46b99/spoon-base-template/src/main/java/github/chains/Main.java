package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generic Spoon transformation that fixes a breaking dependency change:
 * {@code org.cactoos:cactoos} was historically brought transitively by
 * {@code com.artipie:http}, but in v1.1.0 that transitive dependency was removed.
 * Any Maven project that relied on that transitive availability will now fail to
 * compile.
 *
 * <p>Old API pattern (structural, matched by dependency FQN):
 * <pre>
 *   new org.cactoos.text.HexOf(new org.cactoos.io.BytesOf(&lt;byte[] expr&gt;)).asString()
 *   new org.cactoos.list.ListOf&lt;T&gt;(arg1, arg2, ...)
 * </pre>
 *
 * <p>New API pattern (structural, replacement):
 * <pre>
 *   org.apache.commons.codec.binary.Hex.encodeHexString(&lt;byte[] expr&gt;)
 *   java.util.Arrays.asList(arg1, arg2, ...)
 * </pre>
 *
 * <p>The rule is GENERIC: it uses the fully-qualified type names of the
 * DEPENDENCY classes (not the client's class names) as structural match
 * parameters. It can be applied to any project affected by the same breaking
 * change by simply changing the input source directory (via args[0]).
 *
 * <p>Implementation note: Spoon is used to identify the call sites structurally;
 * the actual rewrite is applied as a surgical, position-anchored text substitution
 * so that unrelated formatting and imports in each file remain unchanged.
 */
public class Main {

    // ----- OLD (removed) dependency FQNs: what to match ------------------
    private static final String OLD_HEX_OF      = "org.cactoos.text.HexOf";
    private static final String OLD_BYTES_OF    = "org.cactoos.io.BytesOf";
    private static final String OLD_HEX_METHOD  = "asString";
    private static final String OLD_LIST_OF     = "org.cactoos.list.ListOf";
    private static final String OLD_CACTOOS_PKG = "org.cactoos.";

    // ----- NEW replacement FQNs: what to emit ----------------------------
    private static final String NEW_HEX_CLASS   = "org.apache.commons.codec.binary.Hex";
    private static final String NEW_HEX_METHOD  = "encodeHexString";
    private static final String NEW_LIST_CLASS  = "java.util.Arrays";
    private static final String NEW_LIST_METHOD = "asList";

    // ----- Default input path (overridable via args[0]) ------------------
    private static final String DEFAULT_INPUT = "/workspace/docker-adapter";

    /** Represents a single rewrite: offset in file -> replacement text. */
    private static final class Edit {
        final int start;
        final int end;
        final String replacement;
        Edit(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }
    }

    public static void main(String[] args) throws Exception {
        String projectPath = args.length >= 1 ? args[0] : DEFAULT_INPUT;
        System.out.println("[transformation] Input project path: " + projectPath);
        processDirectory(new File(projectPath, "src/main/java"));
        processDirectory(new File(projectPath, "src/test/java"));
    }

    private static void processDirectory(File srcDir) throws Exception {
        if (!srcDir.exists() || !srcDir.isDirectory()) {
            System.out.println("[transformation] Skipping non-existent directory: " + srcDir);
            return;
        }
        Launcher launcher = new Launcher();
        launcher.addInputResource(srcDir.getAbsolutePath());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setCopyResources(false);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtModel model = launcher.getModel();

        // Collect per-file edits
        Map<File, List<Edit>> fileEdits = new HashMap<>();
        Map<File, Set<Integer>> importLinesToRemove = new HashMap<>();

        int hexFixed  = collectHexFixes(factory, model, fileEdits);
        int listFixed = collectListFixes(factory, model, fileEdits);
        int importsMarked = collectCactoosImports(model, importLinesToRemove);

        // Apply edits
        int filesChanged = 0;
        Set<File> allFiles = new LinkedHashSet<>();
        allFiles.addAll(fileEdits.keySet());
        allFiles.addAll(importLinesToRemove.keySet());
        for (File file : allFiles) {
            if (!applyEdits(file,
                fileEdits.getOrDefault(file, new ArrayList<>()),
                importLinesToRemove.getOrDefault(file, new LinkedHashSet<>()))) {
                continue;
            }
            filesChanged++;
        }

        System.out.println("[transformation] " + srcDir
            + " | HexOf/BytesOf replaced: " + hexFixed
            + " | ListOf replaced: " + listFixed
            + " | cactoos imports marked for removal: " + importsMarked
            + " | Files written: " + filesChanged);
    }

    /**
     * For each {@code new HexOf(new BytesOf(X)).asString()} invocation,
     * record an edit that replaces the full expression text with
     * {@code Hex.encodeHexString(X)}.
     */
    private static int collectHexFixes(Factory factory, CtModel model,
                                       Map<File, List<Edit>> fileEdits) {
        int count = 0;
        for (CtInvocation<?> invocation :
            model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))) {
            if (invocation.getExecutable() == null) {
                continue;
            }
            if (!OLD_HEX_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtConstructorCall)) {
                continue;
            }
            CtConstructorCall<?> hexOf = (CtConstructorCall<?>) target;
            if (!isType(hexOf.getType(), OLD_HEX_OF)) {
                continue;
            }
            List<CtExpression<?>> hexArgs = hexOf.getArguments();
            if (hexArgs.size() != 1) {
                continue;
            }
            CtExpression<?> inner = hexArgs.get(0);
            if (!(inner instanceof CtConstructorCall)) {
                continue;
            }
            CtConstructorCall<?> bytesOf = (CtConstructorCall<?>) inner;
            if (!isType(bytesOf.getType(), OLD_BYTES_OF)) {
                continue;
            }
            List<CtExpression<?>> bytesArgs = bytesOf.getArguments();
            if (bytesArgs.size() != 1) {
                continue;
            }
            CtExpression<?> bytesExpr = bytesArgs.get(0);
            SourcePosition pos = invocation.getPosition();
            if (!pos.isValidPosition() || pos.getFile() == null) {
                continue;
            }
            int start = pos.getSourceStart();
            int end = pos.getSourceEnd();
            String replacement = NEW_HEX_CLASS + "." + NEW_HEX_METHOD + "("
                + bytesExpr.toString() + ")";
            fileEdits.computeIfAbsent(pos.getFile(), k -> new ArrayList<>())
                .add(new Edit(start, end, replacement));
            count++;
        }
        return count;
    }

    /**
     * For each {@code new ListOf<T>(a, b, c)}, record an edit replacing with
     * {@code java.util.Arrays.asList(a, b, c)}.
     */
    private static int collectListFixes(Factory factory, CtModel model,
                                        Map<File, List<Edit>> fileEdits) {
        int count = 0;
        List<CtConstructorCall<?>> matches = new ArrayList<>();
        for (CtConstructorCall<?> call :
            model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class))) {
            if (isType(call.getType(), OLD_LIST_OF)) {
                matches.add(call);
            }
        }
        for (CtConstructorCall<?> call : matches) {
            SourcePosition pos = call.getPosition();
            if (!pos.isValidPosition() || pos.getFile() == null) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(NEW_LIST_CLASS).append(".").append(NEW_LIST_METHOD).append("(");
            List<CtExpression<?>> args = call.getArguments();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(args.get(i).toString());
            }
            sb.append(")");
            fileEdits.computeIfAbsent(pos.getFile(), k -> new ArrayList<>())
                .add(new Edit(pos.getSourceStart(), pos.getSourceEnd(), sb.toString()));
            count++;
        }
        return count;
    }

    /**
     * Mark import lines that reference the old (removed) dependency package
     * so they can be blanked out during the write phase.
     */
    private static int collectCactoosImports(CtModel model,
                                             Map<File, Set<Integer>> linesToRemove) {
        int count = 0;
        Pattern impLine = Pattern.compile("^\\s*import\\s+" + Pattern.quote(OLD_CACTOOS_PKG));
        Set<File> visited = new LinkedHashSet<>();
        for (CtType<?> type : model.getAllTypes()) {
            SourcePosition pos = type.getPosition();
            if (!pos.isValidPosition() || pos.getFile() == null) {
                continue;
            }
            File file = pos.getFile();
            if (!visited.add(file)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (impLine.matcher(line).find()) {
                        linesToRemove.computeIfAbsent(file, k -> new LinkedHashSet<>()).add(i);
                        count++;
                    }
                }
            } catch (Exception e) {
                System.err.println("[transformation] Failed to scan " + file + ": " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * Applies the recorded text edits and import-line removals to the given file.
     * Returns true if the file was actually modified.
     */
    private static boolean applyEdits(File file, List<Edit> edits, Set<Integer> importLines) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            // Apply edits in reverse source-position order to keep offsets valid
            if (!edits.isEmpty()) {
                edits.sort(Comparator.comparingInt((Edit e) -> e.start).reversed());
                for (Edit e : edits) {
                    int endExclusive = Math.min(e.end + 1, content.length());
                    content = content.substring(0, e.start) + e.replacement + content.substring(endExclusive);
                }
            }
            if (!importLines.isEmpty()) {
                String[] lines = content.split("\n", -1);
                // Blank out cactoos import lines
                for (Integer lineIdx : importLines) {
                    if (lineIdx >= 0 && lineIdx < lines.length) {
                        lines[lineIdx] = "";
                    }
                }
                content = String.join("\n", lines);
                // Collapse double blank lines produced by removal into a single one
                content = content.replaceAll("\n{3,}", "\n\n");
            }
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            System.err.println("[transformation] Failed to write " + file + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean isType(CtTypeReference<?> ref, String fqn) {
        if (ref == null) {
            return false;
        }
        return fqn.equals(ref.getQualifiedName());
    }
}
