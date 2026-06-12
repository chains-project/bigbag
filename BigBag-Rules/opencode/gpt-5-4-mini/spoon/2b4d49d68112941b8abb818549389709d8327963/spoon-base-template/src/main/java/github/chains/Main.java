package github.chains;

import spoon.Launcher;
import spoon.OutputType;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        final String inputDirectory = args.length > 0 ? args[0] : ".";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setOutputType(OutputType.COMPILATION_UNITS);
        launcher.setSourceOutputDirectory(new File(inputDirectory));
        launcher.addInputResource(inputDirectory);
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        final CtTypeReference<?> uuidType = factory.createReference("java.util.UUID");

        launcher.getModel().getElements(new TypeFilter<>(CtImport.class)).forEach(importNode -> rewriteImport(importNode));
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(typeReference -> rewriteTypeReference(typeReference, factory));
        launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)).forEach(constructorCall -> rewriteConstructorCall(constructorCall, factory));
        launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).forEach(invocation -> rewriteListenerInvocation(invocation, uuidType, factory));
        launcher.getModel().getElements(new TypeFilter<>(CtMethod.class)).forEach(method -> rewriteRemovedMethod(method));

        launcher.prettyprint();
    }

    private static void rewriteImport(CtImport importNode) {
        final String reference = importNode.getReference() == null ? "" : importNode.getReference().toString();
        switch (reference) {
            case "com.hazelcast.core.MemberAttributeEvent":
                importNode.delete();
                break;
            case "com.hazelcast.core.Cluster":
            case "com.hazelcast.core.Member":
            case "com.hazelcast.core.MembershipEvent":
            case "com.hazelcast.core.MembershipListener":
            case "com.hazelcast.core.IMap":
            case "com.hazelcast.core.MapEvent":
            case "com.hazelcast.monitor.LocalMapStats":
            case "com.hazelcast.config.MaxSizeConfig":
                importNode.delete();
                break;
            default:
                break;
        }
    }

    private static void rewriteTypeReference(CtTypeReference<?> typeReference, Factory factory) {
        final String qn = typeReference.getQualifiedName();
        final String replacement;
        switch (qn) {
            case "com.hazelcast.core.Cluster":
                replacement = "com.hazelcast.cluster.Cluster";
                break;
            case "com.hazelcast.core.Member":
                replacement = "com.hazelcast.cluster.Member";
                break;
            case "com.hazelcast.core.MemberAttributeEvent":
                replacement = "com.hazelcast.cluster.MemberAttributeEvent";
                break;
            case "com.hazelcast.core.MembershipEvent":
                replacement = "com.hazelcast.cluster.MembershipEvent";
                break;
            case "com.hazelcast.core.MembershipListener":
                replacement = "com.hazelcast.cluster.MembershipListener";
                break;
            case "com.hazelcast.core.IMap":
                replacement = "com.hazelcast.map.IMap";
                break;
            case "com.hazelcast.core.MapEvent":
                replacement = "com.hazelcast.map.MapEvent";
                break;
            case "com.hazelcast.monitor.LocalMapStats":
                replacement = "com.hazelcast.map.LocalMapStats";
                break;
            default:
                return;
        }

        typeReference.replace(factory.createReference(replacement));
    }

    private static void rewriteConstructorCall(CtConstructorCall<?> constructorCall, Factory factory) {
        final CtTypeReference<?> type = constructorCall.getType();
        if (type == null || !"com.hazelcast.config.MaxSizeConfig".equals(type.getQualifiedName())) {
            return;
        }

        final String sizeExpr = constructorCall.getArguments().isEmpty() ? "0" : constructorCall.getArguments().get(0).toString();
        final String policyExpr = constructorCall.getArguments().size() > 1
            ? constructorCall.getArguments().get(1).toString()
            : "com.hazelcast.config.MaxSizePolicy.USED_HEAP_SIZE";
        constructorCall.replace(factory.createCodeSnippetExpression(
            "new com.hazelcast.config.EvictionConfig().setSize(" + sizeExpr + ").setMaxSizePolicy(" + policyExpr + ")"
        ));
    }

    private static void rewriteListenerInvocation(CtInvocation<?> invocation, CtTypeReference<?> uuidType, Factory factory) {
        if (!"addEntryListener".equals(invocation.getExecutable().getSimpleName())) {
            return;
        }

        final CtTypeReference<?> targetType = invocation.getExecutable().getDeclaringType();
        if (targetType == null) {
            return;
        }
        final String declaring = targetType.getQualifiedName();
        if (!"com.hazelcast.map.IMap".equals(declaring) && !"com.hazelcast.core.IMap".equals(declaring)) {
            return;
        }

        if (invocation.getType() != null && "java.lang.String".equals(invocation.getType().getQualifiedName())) {
            invocation.setType((CtTypeReference) uuidType.clone());
        }

        if (invocation.getParent() != null && invocation.getParent().toString() != null) {
            if (invocation.getParent().toString().contains("String") && invocation.getParent().toString().contains("addEntryListener")) {
                final String target = invocation.getTarget() == null ? "null" : invocation.getTarget().toString();
                final String args = invocation.getArguments().isEmpty() ? "" : invocation.getArguments().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("");
                invocation.replace(factory.createCodeSnippetExpression(target + ".addEntryListener(" + args + ")"));
            }
        }
    }

    private static void rewriteRemovedMethod(CtMethod<?> method) {
        if (!"memberAttributeChanged".equals(method.getSimpleName())) {
            return;
        }
        if (method.getParameters().size() != 1) {
            return;
        }
        if (method.getParameters().get(0).getType() == null) {
            return;
        }
        if (!"com.hazelcast.core.MemberAttributeEvent".equals(method.getParameters().get(0).getType().getQualifiedName())) {
            return;
        }
        method.delete();
    }
}
