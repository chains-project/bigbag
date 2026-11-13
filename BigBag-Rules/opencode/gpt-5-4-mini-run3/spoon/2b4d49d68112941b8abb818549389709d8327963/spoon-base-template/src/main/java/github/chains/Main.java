package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Main {

    private static final Map<String, String> TYPE_RENAMES = Map.of(
        "com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster",
        "com.hazelcast.core.Member", "com.hazelcast.cluster.Member",
        "com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent",
        "com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener",
        "com.hazelcast.core.IMap", "com.hazelcast.map.IMap",
        "com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent",
        "com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats"
    );

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final Path inputDir = Path.of(args[0]);
        final Path outputDir = args.length > 1 ? Path.of(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-fixed");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        renameTypeReferences(launcher);
        deleteRemovedMemberAttributeCallbacks(launcher);
        rewriteMaxSizeConfigCall(launcher);
        rewriteListenerIdTypes(launcher);

        launcher.prettyprint();
    }

    private static void renameTypeReferences(Launcher launcher) {
        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            final String renamed = TYPE_RENAMES.get(reference.getQualifiedName());
            if (renamed != null) {
                reference.setSimpleName(renamed.substring(renamed.lastIndexOf('.') + 1));
                reference.setPackage(reference.getFactory().Package().createReference(renamed.substring(0, renamed.lastIndexOf('.'))));
            }
        }
    }

    private static void deleteRemovedMemberAttributeCallbacks(Launcher launcher) {
        final List<CtMethod<?>> methods = launcher.getModel().getElements(new TypeFilter<>(CtMethod.class));
        for (CtMethod<?> method : methods) {
            if (!"memberAttributeChanged".equals(method.getSimpleName()) || method.getParameters().size() != 1) {
                continue;
            }
            final CtTypeReference<?> parameterType = method.getParameters().get(0).getType();
            if (parameterType != null && "com.hazelcast.core.MemberAttributeEvent".equals(parameterType.getQualifiedName())) {
                method.delete();
            }
        }
    }

    private static void rewriteMaxSizeConfigCall(Launcher launcher) {
        final List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!"setMaxSizeConfig".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().size() != 1) {
                continue;
            }
            final CtExpression<?> target = invocation.getTarget();
            if (target == null || target.getType() == null || !"com.hazelcast.config.MapConfig".equals(target.getType().getQualifiedName())) {
                continue;
            }
            final String argument = invocation.getArguments().get(0).toString();
            final String size = extractBetween(argument, "(", ",");
            final String policy = extractBetween(argument, ",", ")").replace("MaxSizeConfig.MaxSizePolicy", "com.hazelcast.config.MaxSizePolicy");
            final String replacement = target + ".setEvictionConfig(new com.hazelcast.config.EvictionConfig().setMaxSizePolicy(" + policy.trim() + ").setSize(" + size.trim() + "))";
            final CtStatement statement = invocation.getParent(CtStatement.class);
            if (statement != null) {
                statement.replace(launcher.getFactory().Code().createCodeSnippetStatement(replacement + ";"));
            }
        }
    }

    private static void rewriteListenerIdTypes(Launcher launcher) {
        for (CtVariable<?> variable : launcher.getModel().getElements(new TypeFilter<>(CtVariable.class))) {
            final CtExpression<?> expression = variable instanceof CtLocalVariable<?> local ? local.getDefaultExpression() : null;
            if (expression != null && isListenerRegistration(expression)) {
                replaceStringType(variable, "java.util.UUID");
            }
        }

        final List<CtAssignment<?, ?>> assignments = launcher.getModel().getElements(new TypeFilter<>(CtAssignment.class));
        for (CtAssignment<?, ?> assignment : assignments) {
            if (!isListenerRegistration(assignment.getAssignment())) {
                continue;
            }
            if (assignment.getAssigned() instanceof CtVariableRead<?> read) {
                final CtVariable<?> declaration = read.getVariable().getDeclaration();
                if (declaration != null) {
                    replaceStringType(declaration, "java.util.UUID");
                }
            }
        }
    }

    private static boolean isListenerRegistration(CtExpression<?> expression) {
        if (!(expression instanceof CtInvocation<?> invocation)) {
            return false;
        }
        final String name = invocation.getExecutable().getSimpleName();
        final CtTypeReference<?> targetType = invocation.getTarget() == null ? null : invocation.getTarget().getType();
        if (targetType == null) {
            return false;
        }
        final String qn = targetType.getQualifiedName();
        return ("addMembershipListener".equals(name) && "com.hazelcast.cluster.Cluster".equals(qn))
            || ("addLifecycleListener".equals(name) && "com.hazelcast.core.LifecycleService".equals(qn))
            || ("removeMembershipListener".equals(name) && "com.hazelcast.cluster.Cluster".equals(qn))
            || ("removeLifecycleListener".equals(name) && "com.hazelcast.core.LifecycleService".equals(qn))
            || ("addEntryListener".equals(name) && "com.hazelcast.map.IMap".equals(qn));
    }

    private static void replaceStringType(CtVariable<?> variable, String newType) {
        if (variable.getType() != null && "java.lang.String".equals(variable.getType().getQualifiedName())) {
            variable.setType(variable.getFactory().Type().createReference(newType));
        }
    }

    private static String extractBetween(String text, String startToken, String endToken) {
        final int start = text.indexOf(startToken);
        final int end = text.lastIndexOf(endToken);
        if (start < 0 || end < 0 || end <= start) {
            return text;
        }
        return text.substring(start + startToken.length(), end);
    }
}
