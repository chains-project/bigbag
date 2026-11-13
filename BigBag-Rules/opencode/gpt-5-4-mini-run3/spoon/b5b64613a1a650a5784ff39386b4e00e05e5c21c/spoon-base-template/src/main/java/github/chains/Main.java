package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public class Main {
    private static final String CLIENT_HELPER = "org.jenkinsci.plugins.p4.client.ClientHelper";
    private static final String ITEM = "hudson.model.Item";
    private static final String ITEM_GROUP = "hudson.model.ItemGroup";
    private static final String WORKSPACE = "org.jenkinsci.plugins.p4.workspace.Workspace";
    private static final String ABSTRACT_BUILD = "hudson.model.AbstractBuild";
    private static final String P4_SCM = "org.jenkinsci.plugins.p4.PerforceScm";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        File input = new File(args[0]);
        File output = args.length > 1 ? new File(args[1]) : input;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.getAbsolutePath());
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();

        CtTypeReference<?> clientHelperType = launcher.getFactory().Type().createReference(CLIENT_HELPER);
        CtTypeReference<?> itemType = launcher.getFactory().Type().createReference(ITEM);
        CtTypeReference<?> itemGroupType = launcher.getFactory().Type().createReference(ITEM_GROUP);
        CtTypeReference<?> workspaceType = launcher.getFactory().Type().createReference(WORKSPACE);
        CtTypeReference<?> buildType = launcher.getFactory().Type().createReference(ABSTRACT_BUILD);
        CtTypeReference<?> scmType = launcher.getFactory().Type().createReference(P4_SCM);

        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (!isTargetConstructor(call, clientHelperType) || call.getArguments().size() != 4) {
                continue;
            }

            CtExpression<?> context = resolveContext(call, itemType, itemGroupType, buildType);
            CtExpression<?> workspace = resolveWorkspace(call, workspaceType);
            if (context == null || workspace == null) {
                continue;
            }

            CtConstructorCall<?> replacement = launcher.getFactory().Code()
                    .createConstructorCall(call.getType().clone(),
                            context,
                            call.getArguments().get(0).clone(),
                            call.getArguments().get(1).clone(),
                            workspace);
            call.replace(replacement);
        }

        for (CtInvocation<?> call : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!isAllBlankCall(call)) {
                continue;
            }
            StringBuilder replacement = new StringBuilder();
            List<CtExpression<?>> arguments = call.getArguments();
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    replacement.append(" && ");
                }
                replacement.append("org.apache.commons.lang3.StringUtils.isBlank(")
                        .append(arguments.get(i).toString())
                        .append(")");
            }
            call.replace(call.getFactory().Code().createCodeSnippetExpression(replacement.toString()));
        }

        launcher.prettyprint();
    }

    private static boolean isTargetConstructor(CtConstructorCall<?> call, CtTypeReference<?> targetType) {
        return call.getType() != null && targetType.getQualifiedName().equals(call.getType().getQualifiedName());
    }

    private static boolean isAllBlankCall(CtInvocation<?> call) {
        return call.getExecutable() != null
                && "isAllBlank".equals(call.getExecutable().getSimpleName())
                && call.getTarget() != null
                && call.getTarget().toString().endsWith("StringUtils");
    }

    private static CtExpression<?> resolveContext(CtConstructorCall<?> call,
                                                  CtTypeReference<?> itemType,
                                                  CtTypeReference<?> itemGroupType,
                                                  CtTypeReference<?> buildType) {
        CtExecutable<?> scope = call.getParent(CtExecutable.class);
        if (scope == null) {
            return null;
        }

        CtType<?> declaringType = scope.getParent(CtType.class);
        if (declaringType != null) {
            for (CtField<?> field : declaringType.getFields()) {
            CtTypeReference<?> type = field.getType();
            if (type != null && (type.isSubtypeOf(itemType) || type.isSubtypeOf(itemGroupType) || type.isSubtypeOf(buildType))) {
                return call.getFactory().Code().createCodeSnippetExpression("this." + field.getSimpleName());
            }
        }
        }

        for (CtExpression<?> expression : scope.getElements(new TypeFilter<>(CtExpression.class))) {
            CtTypeReference<?> type = expression.getType();
            if (type == null) {
                continue;
            }
            if (type.isSubtypeOf(itemType) || type.isSubtypeOf(itemGroupType)) {
                return expression.clone();
            }
        }

        for (CtInvocation<?> invocation : scope.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!"getProject".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            CtExpression<?> target = invocation.getTarget();
            if (target != null && target.getType() != null && target.getType().isSubtypeOf(buildType)) {
                return invocation.clone();
            }
        }

        return null;
    }

    private static CtExpression<?> resolveWorkspace(CtConstructorCall<?> call,
                                                    CtTypeReference<?> workspaceType) {
        CtExecutable<?> scope = call.getParent(CtExecutable.class);
        if (scope == null) {
            return null;
        }

        for (CtExpression<?> expression : scope.getElements(new TypeFilter<>(CtExpression.class))) {
            CtTypeReference<?> type = expression.getType();
            if (type != null && type.isSubtypeOf(workspaceType)) {
                return expression.clone();
            }
        }

        for (CtInvocation<?> invocation : scope.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (invocation.getType() == null || !invocation.getType().isSubtypeOf(workspaceType)) {
                continue;
            }
            if ("getWorkspace".equals(invocation.getExecutable().getSimpleName()) || invocation.getTarget() != null) {
                return invocation.clone();
            }
        }

        for (CtInvocation<?> invocation : scope.getElements(new TypeFilter<>(CtInvocation.class))) {
            CtTypeReference<?> invocationType = invocation.getType();
            if (invocationType == null) {
                continue;
            }
            CtType<?> typeDecl = invocationType.getTypeDeclaration();
            if (typeDecl == null) {
                continue;
            }
            boolean hasWorkspaceGetter = typeDecl.getMethodsByName("getWorkspace").stream()
                    .anyMatch(method -> method.getParameters().isEmpty()
                            && method.getType() != null
                            && method.getType().isSubtypeOf(workspaceType));
            if (hasWorkspaceGetter) {
                return call.getFactory().Code().createCodeSnippetExpression(invocation + ".getWorkspace()");
            }
        }

        return null;
    }
}
