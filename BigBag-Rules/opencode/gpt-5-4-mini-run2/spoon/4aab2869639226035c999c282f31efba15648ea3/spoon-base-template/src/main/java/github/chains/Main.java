package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String OLD_STORAGE_TYPE = "com.artipie.asto.factory.Storages";

    private static final String NEW_LOADER_TYPE = "com.artipie.asto.factory.StoragesLoader";

    private static final String NEW_CONFIG_TYPE = "com.artipie.asto.factory.Config.YamlStorageConfig";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }
        final Path source = Paths.get(args[0]);
        final Path output = args.length > 1 ? Paths.get(args[1]) : source;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(source.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        transform(launcher);
        launcher.prettyprint();
    }

    private static void transform(final Launcher launcher) {
        for (final CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (matchesOldNewStorage(invocation)) {
                replaceNewStorageInvocation(invocation);
            }
        }
    }

    private static boolean matchesOldNewStorage(final CtInvocation<?> invocation) {
        return invocation.getExecutable() != null
            && "newStorage".equals(invocation.getExecutable().getSimpleName())
            && invocation.getExecutable().getParameters().size() == 2
            && isType(invocation.getExecutable().getDeclaringType(), OLD_STORAGE_TYPE)
            && invocation.getArguments().size() == 2;
    }

    private static void replaceNewStorageInvocation(final CtInvocation<?> invocation) {
        final CtExpression<?> type = invocation.getArguments().get(0).clone();
        final CtExpression<?> config = invocation.getArguments().get(1).clone();
        final String replacement = "new " + NEW_LOADER_TYPE + "().newObject(" + type
            + ", new " + NEW_CONFIG_TYPE + "(" + config + "))";
        invocation.replace(invocation.getFactory().Code().createCodeSnippetExpression(replacement));
    }

    private static boolean isType(final CtTypeReference<?> reference, final String expected) {
        return reference != null && expected.equals(reference.getQualifiedName());
    }

}
