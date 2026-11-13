package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String GRAPH_BUILDER = "org.apache.maven.shared.dependency.graph.DependencyGraphBuilder";
    private static final String COLLECTOR_BUILDER = "org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder";
    private static final String PROJECT_BUILDING_REQUEST = "org.apache.maven.project.ProjectBuildingRequest";
    private static final String MAVEN_PROJECT = "org.apache.maven.project.MavenProject";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(Path.of(args[0]).toString());
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        new DependencyTreeFixer(launcher).apply();
        launcher.prettyprint();
    }

    private static final class DependencyTreeFixer {
        private final Launcher launcher;

        private DependencyTreeFixer(Launcher launcher) {
            this.launcher = launcher;
        }

        private void apply() {
            launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).forEach(this::rewriteIfNeeded);
        }

        private void rewriteIfNeeded(CtInvocation<?> invocation) {
            if (!isTargetInvocation(invocation)) {
                return;
            }

            List<CtExpression<?>> arguments = invocation.getArguments();
            if (arguments.size() < 2) {
                return;
            }

            CtExpression<?> firstArgument = arguments.get(0);
            if (isType(firstArgument, PROJECT_BUILDING_REQUEST)) {
                return;
            }

            Optional<CtExpression<?>> replacement = findReplacementExpression(invocation, PROJECT_BUILDING_REQUEST);
            if (replacement.isEmpty()) {
                return;
            }

            invocation.setArguments(List.of(replacement.get(), arguments.get(1)));
        }

        private boolean isTargetInvocation(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                return false;
            }

            String simpleName = executable.getSimpleName();
            return ("buildDependencyGraph".equals(simpleName) || "collectDependencyGraph".equals(simpleName))
                && invocation.getArguments().size() == 2;
        }

        private boolean isType(CtExpression<?> expression, String qualifiedName) {
            CtTypeReference<?> type = expression == null ? null : expression.getType();
            return matchesTypeName(type, qualifiedName);
        }

        private Optional<CtExpression<?>> findReplacementExpression(CtInvocation<?> invocation, String typeName) {
            CtTypeReference<?> requiredType = launcher.getFactory().Type().createReference(typeName);
            for (CtElement scope = invocation; scope != null; scope = scope.getParent()) {
                Optional<CtExpression<?>> candidate = findInScope(scope, requiredType);
                if (candidate.isPresent()) {
                    return candidate;
                }
            }
            return Optional.empty();
        }

        private Optional<CtExpression<?>> findInScope(CtElement scope, CtTypeReference<?> requiredType) {
            List<CtField<?>> fields = scope.getElements(new TypeFilter<>(CtField.class));
            Optional<CtExpression<?>> field = fields.stream()
                .filter(variable -> matchesTypeName(variable.getType(), requiredType.getQualifiedName())
                    || matchesTypeName(variable.getType(), requiredType.getSimpleName()))
                .map(this::toExpression)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
            if (field.isPresent()) {
                return field;
            }

            List<CtVariable<?>> variables = scope.getElements(new TypeFilter<>(CtVariable.class));
            Optional<CtExpression<?>> localOrParameter = variables.stream()
                .filter(variable -> matchesTypeName(variable.getType(), requiredType.getQualifiedName())
                    || matchesTypeName(variable.getType(), requiredType.getSimpleName()))
                .sorted(Comparator.comparingInt(v -> v.getPosition() == null ? Integer.MAX_VALUE : v.getPosition().getSourceStart()))
                .map(this::toExpression)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
            if (localOrParameter.isPresent()) {
                return localOrParameter;
            }

            List<CtMethod<?>> methods = scope.getElements(new TypeFilter<>(CtMethod.class));
            return methods.stream()
                .filter(method -> method.getParameters().isEmpty())
                .filter(method -> matchesTypeName(method.getType(), requiredType.getQualifiedName())
                    || matchesTypeName(method.getType(), requiredType.getSimpleName()))
                .findFirst()
                .map(method -> (CtExpression<?>) launcher.getFactory().Code().createCodeSnippetExpression(method.getSimpleName() + "()"));
        }

        private boolean matchesTypeName(CtTypeReference<?> type, String expectedName) {
            if (type == null || expectedName == null) {
                return false;
            }
            String qualifiedName = type.getQualifiedName();
            if (expectedName.equals(qualifiedName)) {
                return true;
            }
            String simpleName = type.getSimpleName();
            return expectedName.equals(simpleName) || expectedName.endsWith('.' + simpleName);
        }

        private Optional<CtExpression<?>> toExpression(CtVariable<?> variable) {
            CtVariableReference<?> reference = variable.getReference();
            if (reference == null) {
                return Optional.of((CtExpression<?>) launcher.getFactory().Code().createCodeSnippetExpression(variable.getSimpleName()));
            }
            return Optional.of((CtExpression<?>) launcher.getFactory().Code().createVariableRead(reference, false));
        }
    }
}
