package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public final class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        if (args.length > 1) {
            launcher.setSourceOutputDirectory(new File(args[1]));
        }

        launcher.buildModel();

        rewriteJettyHandlerServletTypes(launcher);
        rewriteServletRequestCallSites(launcher);
        rewriteJettyConnectorConstructors(launcher);

        launcher.prettyprint();
    }

    private static void rewriteJettyHandlerServletTypes(Launcher launcher) {
        List<CtClass> classes = launcher.getModel().getElements(new TypeFilter<>(CtClass.class));
        for (CtClass<?> type : classes) {
            if (!isJettyHandler(type)) {
                continue;
            }

            List<CtTypeReference> refs = type.getElements(new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference<?> ref : refs) {
                String qn = ref.getQualifiedName();
                if (qn != null && qn.startsWith(OLD_SERVLET_PREFIX)) {
                    ref.replace(ref.getFactory().Type().createReference(NEW_SERVLET_PREFIX + qn.substring(OLD_SERVLET_PREFIX.length())));
                }
            }

            ensureRequestAdapter(type);
        }
    }

    private static void rewriteServletRequestCallSites(Launcher launcher) {
        List<CtInvocation> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || executable.getParameters().isEmpty() || invocation.getArguments().isEmpty()) {
                continue;
            }

            for (int i = 0; i < Math.min(invocation.getArguments().size(), executable.getParameters().size()); i++) {
                CtTypeReference<?> expected = executable.getParameters().get(i);
                CtExpression<?> actual = invocation.getArguments().get(i);
                if (expected == null || actual == null) {
                    continue;
                }

                if (isOldServletRequest(expected) && isNewServletRequest(actual.getType())) {
                    invocation.getArguments().set(i,
                            actual.getFactory().Code().createCodeSnippetExpression("adaptHttpServletRequest(" + actual + ")"));
                }
            }
        }
    }

    private static void rewriteJettyConnectorConstructors(Launcher launcher) {
        List<CtConstructorCall> calls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !OLD_CONNECTOR.equals(type.getQualifiedName())) {
                continue;
            }

            CtExpression<?> serverExpr = findServerExpression(call);
            if (serverExpr == null) {
                continue;
            }

            call.setType(call.getFactory().Type().createReference(NEW_CONNECTOR));
            call.setArguments(java.util.Collections.singletonList(serverExpr.clone()));
        }
    }

    private static boolean isJettyHandler(CtClass<?> type) {
        CtTypeReference<?> superType = type.getSuperclass();
        return superType != null && "org.eclipse.jetty.server.handler.AbstractHandler".equals(superType.getQualifiedName());
    }

    private static boolean isOldServletRequest(CtTypeReference<?> ref) {
        return ref != null && "javax.servlet.http.HttpServletRequest".equals(ref.getQualifiedName());
    }

    private static boolean isNewServletRequest(CtTypeReference<?> ref) {
        return ref != null && "jakarta.servlet.http.HttpServletRequest".equals(ref.getQualifiedName());
    }

    private static void ensureRequestAdapter(CtClass<?> type) {
        for (CtMethod<?> method : type.getMethodsByName("adaptHttpServletRequest")) {
            if (method.getParameters().size() == 1
                    && isOldServletRequest(method.getType())
                    && isNewServletRequest(method.getParameters().get(0).getType())) {
                return;
            }
        }

        CtMethod<Object> adapter = type.getFactory().Core().createMethod();
        adapter.setSimpleName("adaptHttpServletRequest");
        adapter.addModifier(ModifierKind.PRIVATE);
        adapter.addModifier(ModifierKind.STATIC);
        adapter.setType(type.getFactory().Type().createReference("javax.servlet.http.HttpServletRequest"));
        adapter.setParameters(java.util.Collections.singletonList(
                type.getFactory().Core().createParameter()));
        adapter.getParameters().get(0).setSimpleName("request");
        adapter.getParameters().get(0).setType(type.getFactory().Type().createReference("jakarta.servlet.http.HttpServletRequest"));

        CtBlock<?> body = type.getFactory().Core().createBlock();
        body.addStatement(type.getFactory().Code().createCodeSnippetStatement(
                "return (javax.servlet.http.HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(" +
                        "request.getClass().getClassLoader(), " +
                        "new Class[]{javax.servlet.http.HttpServletRequest.class}, " +
                        "(proxy, method, args) -> { try { " +
                        "java.lang.reflect.Method target = request.getClass().getMethod(method.getName(), method.getParameterTypes()); " +
                        "return target.invoke(request, args); } catch (java.lang.NoSuchMethodException e) { return method.invoke(request, args); } " +
                        "catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); } })"));
        adapter.setBody(body);
        type.addMethod(adapter);
    }

    private static CtExpression<?> findServerExpression(CtConstructorCall<?> call) {
        CtInvocation<?> addConnector = call.getParent(CtInvocation.class);
        if (isAddConnectorInvocation(addConnector, call)) {
            return addConnector.getTarget();
        }

        CtLocalVariable<?> local = call.getParent(CtLocalVariable.class);
        if (local != null) {
            CtExpression<?> target = findOwningServerTarget(call, local.getReference());
            if (target != null) {
                return target;
            }
        }

        CtAssignment<?, ?> assignment = call.getParent(CtAssignment.class);
        if (assignment != null && assignment.getAssigned() instanceof CtVariableAccess) {
            CtExpression<?> target = findOwningServerTarget(call, ((CtVariableAccess<?>) assignment.getAssigned()).getVariable());
            if (target != null) {
                return target;
            }
        }

        return null;
    }

    private static boolean isAddConnectorInvocation(CtInvocation<?> invocation, CtConstructorCall<?> connectorCall) {
        return invocation != null
                && "addConnector".equals(invocation.getExecutable().getSimpleName())
                && invocation.getArguments().contains(connectorCall);
    }

    private static CtExpression<?> findOwningServerTarget(CtConstructorCall<?> call, CtVariableReference<?> createdReference) {
        CtExecutable<?> exec = call.getParent(CtExecutable.class);
        if (exec == null || exec.getBody() == null) {
            return null;
        }

        for (CtInvocation<?> invocation : exec.getBody().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!"addConnector".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().isEmpty()) {
                continue;
            }

            CtExpression<?> argument = invocation.getArguments().get(0);
            if (argument instanceof CtVariableAccess && createdReference.equals(((CtVariableAccess<?>) argument).getVariable())) {
                return invocation.getTarget();
            }
        }

        return null;
    }
}
