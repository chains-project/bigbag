package github.chains;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final Map<String, String> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("org.cactoos.collection.CollectionOf", "org.cactoos.list.ListOf");
        TYPES.put("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered");
        TYPES.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        TYPES.put("org.cactoos.list.Sorted", "org.cactoos.iterable.Sorted");
        TYPES.put("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked");
        TYPES.put("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked");
        TYPES.put("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid");
        TYPES.put("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky");
        TYPES.put("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked");
        TYPES.put("org.cactoos.text.JoinedText", "org.cactoos.text.Joined");
        TYPES.put("org.cactoos.text.RandomText", "org.cactoos.text.Randomized");
        TYPES.put("org.cactoos.text.SplitText", "org.cactoos.text.Split");
        TYPES.put("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed");
    }

    private Main() {
        // no instances
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }
        final File source = new File(args[0]);
        final File output = args.length > 1 ? new File(args[1]) : new File(source.getParentFile(), source.getName() + "-spoon-out");
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(source.getAbsolutePath());
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(Main::rewriteType);
        launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).forEach(Main::rewriteInvocation);
        launcher.getModel().getElements(new TypeFilter<>(spoon.reflect.reference.CtExecutableReference.class)).forEach(Main::rewriteExecutableReference);
        launcher.prettyprint();
    }

    private static void rewriteType(final CtTypeReference<?> type) {
        final String replacement = TYPES.get(type.getQualifiedName());
        if (replacement != null) {
            type.replace(type.getFactory().createReference(replacement));
        }
    }

    private static void rewriteInvocation(final CtInvocation<?> invocation) {
        if (!"intValue".equals(invocation.getExecutable().getSimpleName())) {
            return;
        }
        final CtTypeReference<?> targetType = invocation.getTarget() == null ? null : invocation.getTarget().getType();
        if (targetType != null && "org.cactoos.scalar.LengthOf".equals(targetType.getQualifiedName())) {
            invocation.getExecutable().setSimpleName("value");
        }
    }

    private static void rewriteExecutableReference(final spoon.reflect.reference.CtExecutableReference<?> executable) {
        if ("intValue".equals(executable.getSimpleName())) {
            final CtTypeReference<?> declaring = executable.getDeclaringType();
            if (declaring != null && "org.cactoos.scalar.LengthOf".equals(declaring.getQualifiedName())) {
                executable.setSimpleName("value");
            }
        }
    }
}
