package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String PROJECT_BUILDING_REQUEST = "org.apache.maven.project.ProjectBuildingRequest";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected at least one argument: input source directory");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args.length > 1 ? args[1] : args[0]);
        launcher.addProcessor(new DependencyGraphCallFixer());
        launcher.run();
    }

    private static final class DependencyGraphCallFixer extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            String name = invocation.getExecutable() == null ? null : invocation.getExecutable().getSimpleName();
            return "buildDependencyGraph".equals(name) || "collectDependencyGraph".equals(name);
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            if (invocation.getArguments().isEmpty()) {
                return;
            }

            CtExpression<?> firstArgument = invocation.getArguments().get(0);
            CtExpression<?> replacement = findProjectBuildingRequestExpression(invocation);
            if (replacement != null) {
                firstArgument.replace(replacement.clone());
            }
        }

        private boolean isMavenProject(CtExpression<?> expression) {
            return hasType(expression, "org.apache.maven.project.MavenProject");
        }

        private CtExpression<?> findProjectBuildingRequestExpression(CtInvocation<?> invocation) {
            CtElement scope = invocation;
            while (scope != null) {
                if (scope instanceof CtClass) {
                    CtExpression<?> fromExpression = findExpression((CtClass<?>) scope);
                    if (fromExpression != null) {
                        return fromExpression;
                    }
                }

                if (scope instanceof CtMethod) {
                    CtExpression<?> fromMethod = findExpression((CtMethod<?>) scope);
                    if (fromMethod != null) {
                        return fromMethod;
                    }
                }

                scope = scope.getParent();
            }
            return null;
        }

        private CtExpression<?> findExpression(CtClass<?> type) {
            for (CtField<?> field : type.getFields()) {
                if (isProjectBuildingRequest(field)) {
                    return field.getFactory().createVariableRead(field.getReference(), field.hasModifier(spoon.reflect.declaration.ModifierKind.STATIC));
                }
            }

            for (CtMethod<?> method : type.getMethods()) {
                if (method.getParameters().isEmpty() && isProjectBuildingRequest(method.getType())) {
                    return method.getFactory().createInvocation(type.getFactory().createTypeAccess(type.getReference()), method.getReference());
                }
            }

            return null;
        }

        private CtExpression<?> findExpression(CtMethod<?> method) {
            for (CtParameter<?> parameter : method.getParameters()) {
                if (isProjectBuildingRequest(parameter)) {
                    return parameter.getFactory().createVariableRead(parameter.getReference(), false);
                }
            }

            if (method.getBody() != null) {
                for (CtLocalVariable<?> localVariable : method.getBody().getElements(new TypeFilter<>(CtLocalVariable.class))) {
                    if (isProjectBuildingRequest(localVariable)) {
                        return localVariable.getFactory().createVariableRead(localVariable.getReference(), false);
                    }
                }
            }

            CtClass<?> owner = method.getParent(CtClass.class);
            if (owner != null) {
                CtExpression<?> fromType = findExpression(owner);
                if (fromType != null) {
                    return fromType;
                }
            }

            return null;
        }

        private boolean isProjectBuildingRequest(CtElement element) {
            CtTypeReference<?> type = null;
            if (element instanceof CtField) {
                type = ((CtField<?>) element).getType();
            } else if (element instanceof CtParameter) {
                type = ((CtParameter<?>) element).getType();
            } else if (element instanceof CtLocalVariable) {
                type = ((CtLocalVariable<?>) element).getType();
            } else if (element instanceof CtExpression) {
                type = ((CtExpression<?>) element).getType();
            }
            return matches(type, PROJECT_BUILDING_REQUEST);
        }

        private boolean isProjectBuildingRequest(CtTypeReference<?> type) {
            return matches(type, PROJECT_BUILDING_REQUEST);
        }

        private boolean hasType(CtExpression<?> expression, String expectedType) {
            return expression != null && matches(expression.getType(), expectedType);
        }

        private boolean matches(CtTypeReference<?> type, String expectedType) {
            if (type == null) {
                return false;
            }
            String qualifiedName = type.getQualifiedName();
            String simpleName = type.getSimpleName();
            return expectedType.equals(qualifiedName) || expectedType.endsWith(simpleName) || expectedType.endsWith(qualifiedName);
        }
    }
}
