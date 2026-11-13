package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OLD_TYPE = "com.artipie.asto.factory.Storages";

    private static final String OLD_METHOD = "newStorage";

    private static final String NEW_TYPE = "com.artipie.asto.factory.StoragesLoader";

    private static final String NEW_METHOD = "newObject";

    private static final String CONFIG_TYPE = "com.artipie.asto.factory.Config$YamlStorageConfig";

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-src-dir> [output-src-dir]");
        }
        final Path input = Paths.get(args[0]);
        final Path output = args.length > 1 ? Paths.get(args[1]) : input;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new UpdateStorageCalls());
        launcher.run();
    }

    private static final class UpdateStorageCalls extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(final CtInvocation<?> invocation) {
            final CtExecutableReference<?> executable = invocation.getExecutable();
            final CtTypeReference<?> declaring = executable == null ? null : executable.getDeclaringType();
            return executable != null
                && OLD_METHOD.equals(executable.getSimpleName())
                && declaring != null
                && OLD_TYPE.equals(declaring.getQualifiedName())
                && invocation.getArguments().size() == 2;
        }

        @Override
        public void process(final CtInvocation<?> invocation) {
            final CtExpression<?> typeArg = invocation.getArguments().get(0).clone();
            final CtExpression<?> yamlArg = invocation.getArguments().get(1).clone();
            final CtInvocation<?> replacement = this.getFactory().Code().createInvocation(
                this.getFactory().Code().createConstructorCall(
                    this.getFactory().Type().createReference(NEW_TYPE)
                ),
                this.getFactory().Executable().createReference(
                    NEW_TYPE + "#" + NEW_METHOD + "(java.lang.String,com.artipie.asto.factory.Config)"
                ),
                typeArg,
                this.getFactory().Code().createConstructorCall(
                    this.getFactory().Type().createReference(CONFIG_TYPE),
                    yamlArg
                )
            );
            invocation.replace(replacement);
        }
    }
}
