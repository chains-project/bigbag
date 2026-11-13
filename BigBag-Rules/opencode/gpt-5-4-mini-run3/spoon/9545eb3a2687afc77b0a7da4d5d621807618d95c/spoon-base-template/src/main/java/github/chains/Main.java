package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Main {

    private static final Map<String, String> TYPE_RENAMES = new HashMap<>();
    private static final Set<String> UUID_RETURNING_METHODS = Set.of(
        "addMembershipListener",
        "addLifecycleListener"
    );

    static {
        TYPE_RENAMES.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        TYPE_RENAMES.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        TYPE_RENAMES.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        TYPE_RENAMES.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        TYPE_RENAMES.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        TYPE_RENAMES.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        TYPE_RENAMES.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        TYPE_RENAMES.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig");
        TYPE_RENAMES.put("com.hazelcast.config.MaxSizeConfig.MaxSizePolicy", "com.hazelcast.config.MaxSizePolicy");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        final Path inputDir = Paths.get(args[0]);
        final Path outputDir = Paths.get(args.length >= 2 ? args[1] : inputDir.resolveSibling(inputDir.getFileName() + "-rewritten").toString());

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addInputResource(inputDir.toString());

        final CtModel model = launcher.buildModel();
        final var factory = launcher.getFactory();
        final CtTypeReference<?> uuidRef = factory.Type().createReference("java.util.UUID");

        rewriteTypeReferences(model);
        rewriteListenerRegistrationTypes(model, uuidRef);
        rewriteHazelcastApiCalls(model, factory);
        removeObsoleteAttributeChangedHandlers(model);

        launcher.prettyprint();
    }

    private static void rewriteTypeReferences(CtModel model) {
        for (Object item : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            CtTypeReference<?> ref = (CtTypeReference<?>) item;
            final String qualifiedName = ref.getQualifiedName();
            final String replacement = TYPE_RENAMES.get(qualifiedName);
            if (replacement != null) {
                ref.replace(ref.getFactory().Type().createReference(replacement));
            }
        }
    }

    private static void rewriteListenerRegistrationTypes(CtModel model, CtTypeReference<?> uuidRef) {
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!UUID_RETURNING_METHODS.contains(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            final CtElement parent = invocation.getParent();
            if (parent instanceof CtLocalVariable<?> localVariable) {
                if (isStringType(localVariable.getType())) {
                    localVariable.setType(uuidRef.clone());
                }
            } else if (parent instanceof CtAssignment<?, ?> assignment) {
                final CtExpression<?> leftHandSide = assignment.getAssigned();
                if (leftHandSide instanceof CtVariableAccess<?> variableAccess) {
                    final CtVariableReference<?> variableReference = variableAccess.getVariable();
                    final CtVariable<?> declaration = variableReference.getDeclaration();
                    if (declaration != null && isStringType(declaration.getType())) {
                        declaration.setType(uuidRef.clone());
                    }
                }
            } else if (parent instanceof CtField<?> field && isStringType(field.getType())) {
                field.setType(uuidRef.clone());
            }
        }
    }

    private static void rewriteHazelcastApiCalls(CtModel model, spoon.reflect.factory.Factory factory) {

        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            final String name = invocation.getExecutable().getSimpleName();
            if ("getStringAttribute".equals(name)) {
                invocation.getExecutable().setSimpleName("getAttribute");
                continue;
            }

            if ("setStringAttribute".equals(name)) {
                invocation.getExecutable().setSimpleName("setAttribute");
                continue;
            }

            if ("setMaxSizeConfig".equals(name) && !invocation.getArguments().isEmpty()) {
                final CtExpression<?> originalArgument = invocation.getArguments().get(0);
                final String rewritten = rewriteMaxSizeConfigArgument(originalArgument);
                invocation.replace(factory.Code().createCodeSnippetExpression(rewritten));
            }
        }

        for (Object item : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) item;
            final CtTypeReference<?> type = constructorCall.getType();
            if (type != null && "com.hazelcast.config.MaxSizeConfig".equals(type.getQualifiedName())) {
                constructorCall.replace(factory.Code().createCodeSnippetExpression(rewriteMaxSizeConfigConstructor(constructorCall)));
            }
        }
    }

    private static void removeObsoleteAttributeChangedHandlers(CtModel model) {
        for (CtMethod<?> method : model.getElements(new TypeFilter<>(CtMethod.class))) {
            if (!"memberAttributeChanged".equals(method.getSimpleName()) || method.getParameters().size() != 1) {
                continue;
            }
            final CtTypeReference<?> parameterType = method.getParameters().get(0).getType();
            if (parameterType != null && "MemberAttributeEvent".equals(parameterType.getSimpleName())) {
                method.delete();
            }
        }
    }

    private static boolean isStringType(CtTypeReference<?> type) {
        return type != null && "java.lang.String".equals(type.getQualifiedName());
    }

    private static String rewriteMaxSizeConfigArgument(CtExpression<?> originalArgument) {
        if (originalArgument instanceof CtConstructorCall<?> constructorCall) {
            return rewriteMaxSizeConfigConstructor(constructorCall);
        }
        return "new com.hazelcast.config.EvictionConfig().setSize("
            + originalArgument
            + ").setMaxSizePolicy(com.hazelcast.config.MaxSizePolicy.ENTRY_COUNT)";
    }

    private static String rewriteMaxSizeConfigConstructor(CtConstructorCall<?> constructorCall) {
        if (constructorCall.getArguments().size() < 2) {
            return "new com.hazelcast.config.EvictionConfig()";
        }

        final String sizeExpr = constructorCall.getArguments().get(0).toString();
        final String policyExpr = normalizeMaxSizePolicy(constructorCall.getArguments().get(1).toString());
        return "new com.hazelcast.config.EvictionConfig().setSize(" + sizeExpr + ").setMaxSizePolicy(" + policyExpr + ")";
    }

    private static String normalizeMaxSizePolicy(String expression) {
        return expression
            .replace("MaxSizeConfig.MaxSizePolicy", "com.hazelcast.config.MaxSizePolicy")
            .replace("MaxSizeConfig.", "com.hazelcast.config.MaxSizePolicy.");
    }
}
