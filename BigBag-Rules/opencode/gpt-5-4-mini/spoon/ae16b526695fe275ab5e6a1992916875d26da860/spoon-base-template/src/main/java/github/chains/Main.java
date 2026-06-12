package github.chains;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER = "org.eclipse.jetty.server.Server";
    private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String CONNECTOR = "org.eclipse.jetty.server.Connector";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(new File(output.toString()));
        launcher.buildModel();

        CtTypeReference<?> oldConnectorRef = launcher.getFactory().Type().createReference(OLD_CONNECTOR);
        CtTypeReference<?> newConnectorRef = launcher.getFactory().Type().createReference(NEW_CONNECTOR);
        CtTypeReference<?> serverRef = launcher.getFactory().Type().createReference(SERVER);
        CtTypeReference<?> httpConfigurationRef = launcher.getFactory().Type().createReference(HTTP_CONFIGURATION);
        CtTypeReference<?> httpConnectionFactoryRef = launcher.getFactory().Type().createReference(HTTP_CONNECTION_FACTORY);
        CtTypeReference<?> connectorRef = launcher.getFactory().Type().createReference(CONNECTOR);

        List<CtBlock<?>> blocks = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtBlock.class)));
        for (CtBlock<?> block : blocks) {
            boolean needsHttpConfiguration = false;
            for (CtConstructorCall<?> call : block.getElements(new TypeFilter<>(CtConstructorCall.class))) {
                if (sameType(call.getType(), oldConnectorRef)) {
                    needsHttpConfiguration = true;
                    break;
                }
            }
            if (!needsHttpConfiguration) {
                for (CtInvocation<?> invocation : block.getElements(new TypeFilter<>(CtInvocation.class))) {
                    String methodName = invocation.getExecutable().getSimpleName();
                    if (("setSendServerVersion".equals(methodName) || "setSendDateHeader".equals(methodName))
                            && invocation.getTarget() != null
                            && sameType(invocation.getTarget().getType(), serverRef)) {
                        needsHttpConfiguration = true;
                        break;
                    }
                }
            }
            if (!needsHttpConfiguration) {
                continue;
            }

            CtLocalVariable<?> httpConfigurationVar = ensureHttpConfiguration(block, launcher.getFactory(), httpConfigurationRef);

            for (Object invocationObj : new ArrayList<>(block.getElements(new TypeFilter<>(CtInvocation.class)))) {
                CtInvocation<?> invocation = (CtInvocation<?>) invocationObj;
                String methodName = invocation.getExecutable().getSimpleName();
                if (!"setSendServerVersion".equals(methodName) && !"setSendDateHeader".equals(methodName)) {
                    continue;
                }
                CtExpression<?> target = invocation.getTarget();
                if (target == null || !sameType(target.getType(), serverRef)) {
                    continue;
                }
                invocation.setTarget(launcher.getFactory().Code().createVariableRead(httpConfigurationVar.getReference(), false));
            }

            for (Object callObj : new ArrayList<>(block.getElements(new TypeFilter<>(CtConstructorCall.class)))) {
                CtConstructorCall<?> call = (CtConstructorCall<?>) callObj;
                if (!sameType(call.getType(), oldConnectorRef)) {
                    continue;
                }
                CtExpression<?> serverExpr = findServerExpression(block, launcher.getFactory(), serverRef);
                CtConstructorCall<?> httpConnectionFactoryCall = launcher.getFactory().Core().createConstructorCall();
                httpConnectionFactoryCall.setType(httpConnectionFactoryRef);
                httpConnectionFactoryCall.addArgument(launcher.getFactory().Code().createVariableRead(httpConfigurationVar.getReference(), false));

                CtConstructorCall<?> serverConnectorCall = launcher.getFactory().Core().createConstructorCall();
                serverConnectorCall.setType(newConnectorRef);
                serverConnectorCall.addArgument(serverExpr.clone());
                serverConnectorCall.addArgument(httpConnectionFactoryCall);
                call.replace(serverConnectorCall);

                promoteAssignedDeclaration(call, newConnectorRef, connectorRef);
            }
        }

        List<CtTypeReference<?>> connectorTypes = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> ref : connectorTypes) {
            if (sameType(ref, oldConnectorRef)) {
                ref.replace(newConnectorRef.clone());
            }
        }

        for (CtImport importDecl : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            CtReference imported = importDecl.getReference();
            if (imported instanceof CtTypeReference && sameType((CtTypeReference<?>) imported, oldConnectorRef)) {
                importDecl.delete();
            }
        }

        for (CtField<?> field : launcher.getModel().getElements(new TypeFilter<>(CtField.class))) {
            if (sameType(field.getType(), connectorRef) && hasServerConnectorInitializer(field, oldConnectorRef)) {
                field.setType(newConnectorRef.clone());
            }
        }

        launcher.prettyprint();
    }

    private static boolean hasServerConnectorInitializer(CtField<?> field, CtTypeReference<?> oldConnectorRef) {
        CtExpression<?> defaultExpression = field.getDefaultExpression();
        return defaultExpression instanceof CtConstructorCall && sameType(((CtConstructorCall<?>) defaultExpression).getType(), oldConnectorRef);
    }

    private static void promoteAssignedDeclaration(CtElement element, CtTypeReference<?> newType, CtTypeReference<?> connectorType) {
        CtElement parent = element.getParent();
        if (!(parent instanceof CtAssignment)) {
            return;
        }
        CtExpression<?> assigned = ((CtAssignment<?, ?>) parent).getAssigned();
        if (assigned instanceof CtVariableAccess) {
            CtVariableAccess<?> access = (CtVariableAccess<?>) assigned;
            if (access.getVariable() != null && access.getVariable().getDeclaration() != null && sameType(access.getVariable().getDeclaration().getType(), connectorType)) {
                access.getVariable().getDeclaration().setType(newType.clone());
            }
        }
    }

    private static CtLocalVariable<?> ensureHttpConfiguration(CtBlock<?> block, Factory factory, CtTypeReference<?> httpConfigurationRef) {
        for (CtElement element : block.getStatements()) {
            if (element instanceof CtLocalVariable) {
                CtLocalVariable<?> local = (CtLocalVariable<?>) element;
                if ("httpConfiguration".equals(local.getSimpleName()) && sameType(local.getType(), httpConfigurationRef)) {
                    return local;
                }
            }
        }

        CtLocalVariable<?> local = factory.Core().createLocalVariable();
        local.setSimpleName("httpConfiguration");
        local.setType(httpConfigurationRef.clone());
        CtConstructorCall<?> init = factory.Core().createConstructorCall();
        init.setType(httpConfigurationRef.clone());
        local.setDefaultExpression((CtExpression) init);
        if (!block.getStatements().isEmpty()) {
            CtStatement first = block.getStatements().get(0);
            if (first instanceof CtInvocation) {
                first.insertAfter(local);
            } else {
                first.insertBefore(local);
            }
        } else {
            block.addStatement(local);
        }
        return local;
    }

    private static CtExpression<?> findServerExpression(CtBlock<?> block, Factory factory, CtTypeReference<?> serverRef) {
        for (CtElement element : block.getStatements()) {
            if (element instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) element;
                if ("addConnector".equals(invocation.getExecutable().getSimpleName()) && invocation.getTarget() != null && sameType(invocation.getTarget().getType(), serverRef)) {
                    return invocation.getTarget();
                }
            }
        }
        for (CtElement element : block.getStatements()) {
            if (element instanceof CtLocalVariable) {
                CtLocalVariable<?> local = (CtLocalVariable<?>) element;
                if (sameType(local.getType(), serverRef) && local.getDefaultExpression() instanceof CtConstructorCall && sameType(((CtConstructorCall<?>) local.getDefaultExpression()).getType(), serverRef)) {
                    return factory.Code().createVariableRead(local.getReference(), false);
                }
            }
        }
        for (CtElement element : block.getStatements()) {
            if (element instanceof CtAssignment) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) element;
                if (assignment.getAssignment() instanceof CtConstructorCall && sameType(((CtConstructorCall<?>) assignment.getAssignment()).getType(), serverRef)) {
                    CtExpression<?> assigned = assignment.getAssigned();
                    if (assigned instanceof CtVariableAccess) {
                        CtVariableAccess<?> access = (CtVariableAccess<?>) assigned;
                        if (access.getVariable() != null) {
                            return factory.Code().createVariableRead(access.getVariable(), false);
                        }
                    }
                    return assigned;
                }
            }
        }
        return factory.Code().createCodeSnippetExpression("server");
    }

    private static boolean sameType(CtTypeReference<?> type, CtTypeReference<?> expected) {
        return type != null && expected != null && expected.getQualifiedName().equals(type.getQualifiedName());
    }
}
