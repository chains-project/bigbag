package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;

public class Main {

    private static final String OLD_OWNER = "org.apache.maven.shared.dependency.graph.DependencyGraphBuilder";
    private static final String OLD_METHOD = "buildDependencyGraph";
    private static final String OLD_FIRST_ARG = "org.apache.maven.project.MavenProject";
    private static final String NEW_FIRST_ARG = "org.apache.maven.project.ProjectBuildingRequest";
    private static final String SESSION_TYPE = "org.apache.maven.execution.MavenSession";
    private static final String SESSION_GETTER = "getProjectBuildingRequest";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new Rule());

        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static final class Rule extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (!isTargetInvocation(invocation) || invocation.getArguments().isEmpty()) {
                return;
            }

            CtExpression<?> first = invocation.getArguments().get(0);
            if (first == null || first.getType() == null || !OLD_FIRST_ARG.equals(first.getType().getQualifiedName())) {
                return;
            }

            CtExpression<?> replacement = resolveReplacement(invocation);
            if (replacement != null) {
                first.replace(replacement);
            }
        }
    }

    private static boolean isTargetInvocation(CtInvocation<?> invocation) {
        return invocation.getExecutable() != null
                && OLD_METHOD.equals(invocation.getExecutable().getSimpleName())
                && invocation.getExecutable().getDeclaringType() != null
                && OLD_OWNER.equals(invocation.getExecutable().getDeclaringType().getQualifiedName());
    }

    private static CtExpression<?> resolveReplacement(CtInvocation<?> invocation) {
        CtType<?> owner = invocation.getParent(CtType.class);
        if (owner == null) {
            return null;
        }

        CtTypeReference<?> newType = invocation.getFactory().Type().createReference(NEW_FIRST_ARG);
        CtExpression<?> replacement = findDirectValue(owner, invocation, newType);
        if (replacement != null) {
            return replacement;
        }

        CtExpression<?> session = findSessionAccess(owner, invocation);
        if (session == null) {
            return null;
        }

        CtType<?> sessionType = invocation.getFactory().Type().createReference(SESSION_TYPE).getTypeDeclaration();
        if (sessionType == null) {
            return null;
        }

        CtMethod<?> getter = sessionType.getMethodsByName(SESSION_GETTER).stream().findFirst().orElse(null);
        if (getter == null) {
            return null;
        }

        return (CtExpression<?>) invocation.getFactory().Code().createInvocation(session, getter.getReference());
    }

    private static CtExpression<?> findDirectValue(CtType<?> owner, CtInvocation<?> invocation, CtTypeReference<?> desiredType) {
        CtExpression<?> fromField = owner.getFields().stream()
                .filter(field -> field.getType() != null && desiredType.getQualifiedName().equals(field.getType().getQualifiedName()))
                .map(field -> (CtExpression<?>) invocation.getFactory().Code().createVariableRead(field.getReference(), field.isStatic()))
                .findFirst()
                .orElse(null);
        if (fromField != null) {
            return fromField;
        }

        CtExecutable<?> executable = invocation.getParent(CtExecutable.class);
        if (executable != null) {
            CtExpression<?> fromParam = executable.getParameters().stream()
                    .filter(parameter -> parameter.getType() != null && desiredType.getQualifiedName().equals(parameter.getType().getQualifiedName()))
                    .map(parameter -> (CtExpression<?>) invocation.getFactory().Code().createVariableRead(parameter.getReference(), false))
                    .findFirst()
                    .orElse(null);
            if (fromParam != null) {
                return fromParam;
            }
        }

        return owner.getMethods().stream()
                .filter(method -> method.getParameters().isEmpty() && method.getType() != null && desiredType.getQualifiedName().equals(method.getType().getQualifiedName()))
                .map(Main::invokeMethod)
                .findFirst()
                .orElse(null);
    }

    private static CtExpression<?> findSessionAccess(CtType<?> owner, CtInvocation<?> invocation) {
        CtExpression<?> fromField = owner.getFields().stream()
                .filter(field -> field.getType() != null && SESSION_TYPE.equals(field.getType().getQualifiedName()))
                .map(field -> (CtExpression<?>) invocation.getFactory().Code().createVariableRead(field.getReference(), field.isStatic()))
                .findFirst()
                .orElse(null);
        if (fromField != null) {
            return fromField;
        }

        CtExecutable<?> executable = invocation.getParent(CtExecutable.class);
        if (executable != null) {
            CtExpression<?> fromParam = executable.getParameters().stream()
                    .filter(parameter -> parameter.getType() != null && SESSION_TYPE.equals(parameter.getType().getQualifiedName()))
                    .map(parameter -> (CtExpression<?>) invocation.getFactory().Code().createVariableRead(parameter.getReference(), false))
                    .findFirst()
                    .orElse(null);
            if (fromParam != null) {
                return fromParam;
            }
        }

        return owner.getMethods().stream()
                .filter(method -> method.getParameters().isEmpty() && method.getType() != null && SESSION_TYPE.equals(method.getType().getQualifiedName()))
                .map(Main::invokeMethod)
                .findFirst()
                .orElse(null);
    }

    private static CtExpression<?> invokeMethod(CtMethod<?> method) {
        CtType<?> owner = method.getParent(CtType.class);
        CtExpression<?> target = method.isStatic() || owner == null ? null : method.getFactory().Code().createThisAccess(owner.getReference());
        return (CtExpression<?>) method.getFactory().Code().createInvocation(target, method.getReference());
    }
}
