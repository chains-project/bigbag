package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final String OLD_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
    private static final String BUILDER_TYPE = "org.apache.maven.shared.dependency.graph.DependencyGraphBuilder";
    private static final String RESOLVER_TYPE = "org.apache.maven.project.ProjectDependenciesResolver";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
                CtTypeReference<?> type = candidate.getType();
                return type != null && OLD_TYPE.equals(type.getQualifiedName());
            }

            @Override
            public void process(CtConstructorCall<?> call) {
                CtExpression<?> replacement = buildReplacement(call);
                if (replacement != null) {
                    call.replace(replacement);
                }
            }
        });

        launcher.run();
    }

    private static CtExpression<?> buildReplacement(CtElement element) {
        Optional<CtExpression<?>> existingBuilder = findAccessibleValue(element, BUILDER_TYPE);
        if (existingBuilder.isPresent()) {
            return existingBuilder.get().clone();
        }

        Optional<CtExpression<?>> resolver = findAccessibleValue(element, RESOLVER_TYPE);
        if (resolver.isPresent()) {
            CtConstructorCall<?> ctorCall = element.getFactory().Code()
                .createConstructorCall(element.getFactory().Type().createReference(NEW_TYPE));
            ctorCall.addArgument(resolver.get().clone());
            return ctorCall;
        }

        return element.getFactory().Code().createCodeSnippetExpression("null");
    }

    private static Optional<CtExpression<?>> findAccessibleValue(CtElement element, String qualifiedTypeName) {
        CtTypeReference<?> desiredType = element.getFactory().Type().createReference(qualifiedTypeName);

        CtExecutable<?> executable = element.getParent(CtExecutable.class);
        if (executable != null) {
            List<?> parameters = executable.getParameters();
            for (Object parameter : parameters) {
                CtVariableReference<?> reference = ((spoon.reflect.declaration.CtParameter<?>) parameter).getReference();
                if (reference.getType() != null && desiredType.equals(reference.getType())) {
                    return Optional.of(element.getFactory().Code().createVariableRead(reference, false));
                }
            }
        }

        CtType<?> type = element.getParent(CtType.class);
        while (type != null) {
            for (CtField<?> field : type.getFields()) {
                CtVariableReference<?> reference = field.getReference();
                if (reference.getType() != null && desiredType.equals(reference.getType())) {
                    return Optional.of(element.getFactory().Code().createVariableRead(reference, false));
                }
            }
            type = type.getDeclaringType();
        }

        return Optional.empty();
    }
}
