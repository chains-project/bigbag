package github.chains;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_OWNER = "org.mapstruct.ap.spi.BuilderInfo.Builder";
    private static final String OLD_METHOD = "buildMethod";
    private static final String COLLECTIONS_OWNER = "java.util.Collections";
    private static final String SINGLETON_LIST = "singletonList";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path input = Path.of(args[0]);
        Path output = args.length == 2 ? Path.of(args[1]) : input.resolveSibling(input.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new BuilderMethodCollectionProcessor());
        launcher.run();
    }

    private static final class BuilderMethodCollectionProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();
            CtTypeReference<?> declaringType = executable == null ? null : executable.getDeclaringType();
            CtTypeReference<?> firstParameter = executable != null && !executable.getParameters().isEmpty()
                    ? executable.getParameters().get(0)
                    : null;
            return executable != null
                    && OLD_METHOD.equals(executable.getSimpleName())
                    && declaringType != null
                    && OLD_OWNER.equals(declaringType.getQualifiedName())
                    && candidate.getArguments().size() == 1
                    && firstParameter != null
                    && "javax.lang.model.element.ExecutableElement".equals(firstParameter.getQualifiedName());
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            CtExpression<?> argument = invocation.getArguments().get(0);
            CtInvocation<?> replacement = createWrappedInvocation(invocation, argument);
            invocation.replace(replacement);
        }

        private CtInvocation<?> createWrappedInvocation(CtInvocation<?> original, CtExpression<?> argument) {
            CtTypeReference<?> collectionsType = getFactory().Type().createReference(COLLECTIONS_OWNER);
            CtExecutableReference<?> singletonList = getFactory().createExecutableReference();
            singletonList.setDeclaringType(collectionsType);
            singletonList.setSimpleName(SINGLETON_LIST);
            singletonList.setStatic(true);
            singletonList.setType(getFactory().createCtTypeReference(List.class));
            singletonList.setParameters(List.of(argument.getType()));

            CtExpression<?> target = getFactory().Code().createTypeAccess(collectionsType);
            CtInvocation<?> wrappedArgument = getFactory().createInvocation(target, singletonList, argument.clone());
            CtExecutableReference<?> updatedExecutable = original.getExecutable().clone();
            updatedExecutable.setParameters(List.of(getFactory().createCtTypeReference(Collection.class)));
            return getFactory().createInvocation(original.getTarget(), updatedExecutable, wrappedArgument);
        }
    }
}
