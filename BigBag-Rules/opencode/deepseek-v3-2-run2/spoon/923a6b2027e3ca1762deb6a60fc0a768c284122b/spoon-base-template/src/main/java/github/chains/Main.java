package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic Spoon transformation rule fixing a breaking generic-signature change
 * of an external dependency at ALL consumer call sites of any Maven/Java project.
 *
 * <p>The modeled breaking change (org.hamcrest:hamcrest 1.x to 2.2):
 * <pre>
 *   OLD API (hamcrest &lt;= 1.3):
 *     static &lt;U&gt; org.hamcrest.Matcher&lt;java.lang.Iterable&lt;U&gt;&gt; everyItem(Matcher&lt;U&gt; item)
 *   NEW API (hamcrest &gt;= 2.2):
 *     static &lt;U&gt; org.hamcrest.Matcher&lt;java.lang.Iterable&lt;? extends U&gt;&gt; everyItem(Matcher&lt;U&gt; item)
 * </pre>
 *
 * <p>Because {@code Matcher} is invariant in its type argument, every consumer that
 * declared the OLD result shape {@code Matcher<Iterable<X>>} (method parameters,
 * local variables, fields, method return types) no longer accepts the factory result.
 *
 * <p>The structural transformation applied at each consumer site widens the declared
 * type from {@code Matcher<Iterable<X>>} to {@code Matcher<? super Iterable<X>>},
 * which accepts both the old exact shape and the new wildcard shape, keeping all
 * other usages source-compatible.
 *
 * <p>The rule is parameterized ONLY by fully-qualified type/method names belonging to
 * the DEPENDENCY (see CONFIG section) - it contains no client-project identifiers.
 * Apply it to any affected project by passing the project source directory:
 * <pre>java github.chains.Main &lt;input-source-root&gt;</pre>
 * Files are patched in place, preserving all original formatting and comments.
 */
public final class Main {

    // ------------------------------------------------------------------
    // CONFIG: specification of the changed dependency API.
    // Keys are fully-qualified receiver types of the dependency,
    // values are factory method names whose result type changed.
    // ------------------------------------------------------------------
    private static final Map<String, Set<String>> CHANGED_FACTORIES;
    static {
        final Map<String, Set<String>> map = new HashMap<>();
        map.put("org.hamcrest.Matchers", unmodifiable(
            "everyItem"));
        map.put("org.hamcrest.CoreMatchers", unmodifiable(
            "everyItem"));
        map.put("org.hamcrest.collection.IsIterableContaining", unmodifiable(
            "everyItem"));
        CHANGED_FACTORIES = Collections.unmodifiableMap(map);
    }

    /** FQN of the dependency result type whose type argument widened. */
    private static final String MATCHER_FQN = "org.hamcrest.Matcher";
    /** FQN of the collection type inside the dependency result type. */
    private static final String COLLECTION_FQN = "java.lang.Iterable";
    /**
     * Widening applied to the top-level type argument of the consumer's declared
     * type: {@code Matcher<Iterable<X>> -> Matcher<? super Iterable<X>>}.
     */
    private static final String WILDCARD_PREFIX = "? super ";

    private static Set<String> unmodifiable(final String... names) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
    }

    /** A pending text edit: replace [start,end) of a file with new text. */
    private record Patch(Path file, int start, int end, String replacement) {
    }

    /**
     * Finds every usage of a changed factory from the dependency and widens the
     * declared {@code Matcher<Iterable<X>>} types of its consumers.
     */
    public static class WidenConsumersProcessor extends AbstractProcessor<CtInvocation<?>> {

        /** Accumulated edits keyed by target file. */
        private final Map<Path, List<Patch>> patches = new HashMap<>();
        /** Locations already patched (deduplication). */
        private final Set<String> seen = new HashSet<>();

        @Override
        public void process(final CtInvocation<?> invocation) {
            if (!isChangedFactoryCall(invocation)) {
                return;
            }
            System.out.println("FOUND changed factory call: " + invocation);
            System.out.println("DEBUG - parent: " + invocation.getParent().getClass().getSimpleName());
            CtElement current = invocation;
            while (true) {
                final CtElement parent = current.getParent();
                if (parent == null) {
                    break;
                }
                if (parent instanceof CtInvocation) {
                    this.widenArgumentParameter((CtInvocation<?>) parent, current);
                    current = parent;
                    continue;
                }
                if (parent instanceof CtLocalVariable) {
                    this.widenTypeOf(
                        ((CtLocalVariable<?>) parent).getType());
                    break;
                }
                if (parent instanceof CtField) {
                    this.widenTypeOf(((CtField<?>) parent).getType());
                    break;
                }
                if (parent instanceof CtAssignment) {
                    final CtElement assigned =
                        ((CtAssignment<?, ?>) parent).getAssigned();
                    if (assigned instanceof CtVariableWrite) {
                        final CtVariable<?> decl =
                            ((CtVariableWrite<?>) assigned).getVariable()
                                .getDeclaration();
                        if (decl != null) {
                            this.widenTypeOf(decl.getType());
                        }
                    }
                    break;
                }
                if (parent instanceof CtReturn) {
                    final CtExecutable<?> behavior = parent
                        .getParent(CtExecutable.class);
                    if (behavior != null) {
                        this.widenTypeOf(behavior.getType());
                    }
                    break;
                }
                break;
            }
        }

        /** Checks the invocation against the changed dependency API spec. */
        private boolean isChangedFactoryCall(final CtInvocation<?> invocation) {
            final CtExecutableReference<?> exec = invocation.getExecutable();
            final CtTypeReference<?> declaring = exec.getDeclaringType();
            if (declaring == null) {
                return false;
            }
            final Set<String> methods =
                CHANGED_FACTORIES.get(declaring.getQualifiedName());
            return methods != null
                && methods.contains(exec.getSimpleName());
        }

        /**
         * If the factory result is passed as an argument to a method declared in
         * the analyzed sources, widens that source-declared parameter's type.
         */
        private void widenArgumentParameter(
            final CtInvocation<?> call, final CtElement argument) {
            System.out.println("DEBUG widenArgumentParameter: call=" + call + ", exec=" + call.getExecutable());
            final CtExecutableReference<?> exec = call.getExecutable();
            final CtExecutable<?> declaration = exec.getDeclaration();
            System.out.println("DEBUG - declaration: " + declaration);
            if (declaration == null) {
                System.out.println("DEBUG - declaration is null (dependency method)");
                return;
            }
            final List<?> arguments = call.getArguments();
            int index = -1;
            for (int i = 0; i < arguments.size(); i = i + 1) {
                if (arguments.get(i) == argument) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                return;
            }
            final List<CtParameter<?>> params = declaration.getParameters();
            if (params.isEmpty()) {
                return;
            }
            final int pidx = Math.min(index, params.size() - 1);
            this.widenTypeOf(params.get(pidx).getType());
        }

        /**
         * Core transformation: if the given reference has the old dependency
         * result shape {@code Matcher<Iterable<X>>}, schedule a text edit at its
         * source position widening it to {@code Matcher<? super Iterable<X>>}.
         */
        private void widenTypeOf(final CtTypeReference<?> reference) {
            System.out.println("DEBUG widenTypeOf called with: " + reference + " (qual: " + (reference != null ? reference.getQualifiedName() : "null") + ")");
            if (reference == null
                || !MATCHER_FQN.equals(reference.getQualifiedName())) {
                System.out.println("DEBUG - not a Matcher reference");
                return;
            }
            final List<CtTypeReference<?>> args =
                reference.getActualTypeArguments();
            System.out.println("DEBUG - type args: " + args);
            if (args.size() != 1
                || !COLLECTION_FQN.equals(args.get(0).getQualifiedName())) {
                System.out.println("DEBUG - not Matcher<Iterable<X>>");
                return;
            }
            if (!reference.getPosition().isValidPosition()
                || reference.getPosition().getFile() == null) {
                return;
            }
            final Path file = reference.getPosition().getFile().toPath();
            final int start = reference.getPosition().getSourceStart();
            final int stop = reference.getPosition().getSourceEnd();
            final String key = file + ":" + start + ":" + stop;
            if (!this.seen.add(key)) {
                return;
            }
            try {
                final String text = new String(
                    Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (start < 0 || stop >= text.length() || start >= stop) {
                    return;
                }
                final String original = text.substring(start, stop + 1);
                final int bracket = original.indexOf('<');
                if (bracket < 0 || original.substring(bracket)
                    .contains("?")) {
                    return;
                }
                final String widened = original.substring(0, bracket + 1)
                    + WILDCARD_PREFIX
                    + original.substring(bracket + 1);
                this.patches
                    .computeIfAbsent(file, k -> new ArrayList<>())
                    .add(new Patch(file, start, stop + 1, widened));
                System.out.println(
                    "TRANSFORM: " + file.getFileName()
                    + " '" + original + "' -> '" + widened + "'");
            } catch (final IOException exception) {
                System.err.println(
                    "Cannot read " + file + ": " + exception.getMessage());
            }
        }

        /** Writes all accumulated widening edits back to the original files. */
        @Override
        public void processingDone() {
            for (final Map.Entry<Path, List<Patch>> entry
                : this.patches.entrySet()) {
                final Path file = entry.getKey();
                final List<Patch> list = entry.getValue();
                list.sort(Comparator.comparing(Patch::start).reversed());
                try {
                    StringBuilder text = new StringBuilder(
                        new String(
                            Files.readAllBytes(file),
                            StandardCharsets.UTF_8));
                    for (final Patch patch : list) {
                        text.replace(patch.start(), patch.end(),
                            patch.replacement());
                    }
                    Files.write(file,
                        text.toString().getBytes(StandardCharsets.UTF_8));
                } catch (final IOException exception) {
                    System.err.println(
                        "Cannot write " + file + ": "
                        + exception.getMessage());
                }
            }
            System.out.println(
                "Done. Applied " + this.seen.size()
                + " consumer type widening(s) for the changed dependency API.");
        }
    }

    /**
     * Runs the generic transformation over a project.
     * @param args args[0] = root directory of the project sources to transform
     *             (e.g. /path/to/project/src), any project affected by the
     *             modeled dependency change
     */
    public static void main(final String[] args) {
        if (args.length < 1) {
            System.err.println(
                "Usage: java github.chains.Main <input-source-directory>");
            System.exit(1);
            return;
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory("/tmp/spoon-transform-output");
        launcher.addProcessor(new WidenConsumersProcessor());
        launcher.run();
    }
}