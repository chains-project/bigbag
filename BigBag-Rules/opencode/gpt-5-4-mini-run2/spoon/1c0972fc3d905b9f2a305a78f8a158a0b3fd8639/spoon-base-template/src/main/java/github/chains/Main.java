package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class Main {
    private static final String OLD_BUILDER = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_BUILDER = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String RESOLVER_TYPE = "org.apache.maven.project.ProjectDependenciesResolver";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new DependencyGraphBuilderMigrationProcessor());
        launcher.run();
    }

    private static final class DependencyGraphBuilderMigrationProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            return candidate.getType() != null && OLD_BUILDER.equals(candidate.getType().getQualifiedName());
        }

        @Override
        public void process(CtConstructorCall<?> call) {
            CtExpression<?> resolverReceiver = findResolverAccess(call);
            if (resolverReceiver == null) {
                throw new IllegalStateException("Unable to locate a ProjectDependenciesResolver reference near " + call);
            }

            CtTypeReference<?> newBuilderType = getFactory().createReference(NEW_BUILDER);
            CtConstructorCall<?> replacement = getFactory().createConstructorCall(newBuilderType, resolverReceiver);
            call.replace(replacement);
        }

        private CtExpression<?> findResolverAccess(CtConstructorCall<?> call) {
            CtExecutable<?> executable = call.getParent(CtExecutable.class);
            if (executable != null) {
                for (CtParameter<?> parameter : executable.getParameters()) {
                    if (isResolverType(parameter.getType())) {
                        return getFactory().createVariableRead(parameter.getReference(), false);
                    }
                }

                for (CtElement element : executable.getElements(e -> e instanceof CtLocalVariable)) {
                    CtLocalVariable<?> localVariable = (CtLocalVariable<?>) element;
                    if (isResolverType(localVariable.getType())) {
                        return getFactory().createVariableRead(localVariable.getReference(), false);
                    }
                }
            }

            CtType<?> ownerType = call.getParent(CtType.class);
            if (ownerType != null) {
                for (CtField<?> field : ownerType.getFields()) {
                    if (isResolverType(field.getType())) {
                        CtVariableReference<?> reference = field.getReference();
                        return getFactory().createVariableRead(reference, false);
                    }
                }
            }

            return null;
        }

        private boolean isResolverType(CtTypeReference<?> typeReference) {
            return typeReference != null && Objects.equals(RESOLVER_TYPE, typeReference.getQualifiedName());
        }
    }
}
