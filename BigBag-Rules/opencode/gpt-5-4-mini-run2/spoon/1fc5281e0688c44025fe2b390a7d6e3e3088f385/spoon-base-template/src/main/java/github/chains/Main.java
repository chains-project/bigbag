package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_HANDLER = "org.eclipse.jetty.server.handler.AbstractHandler";
    private static final String OLD_SERVER = "org.eclipse.jetty.server.Server";
    private static final String OLD_HTTP_REQ = "javax.servlet.http.HttpServletRequest";
    private static final String OLD_HTTP_RESP = "javax.servlet.http.HttpServletResponse";
    private static final String OLD_SERVLET_EX = "javax.servlet.ServletException";
    private static final String NEW_HTTP_REQ = "jakarta.servlet.http.HttpServletRequest";
    private static final String NEW_HTTP_RESP = "jakarta.servlet.http.HttpServletResponse";
    private static final String NEW_SERVLET_EX = "jakarta.servlet.ServletException";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-dir> <output-dir>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtTypeReference<?> newConnectorRef = factory.Type().createReference(NEW_CONNECTOR);
        CtTypeReference<?> oldConnectorRef = factory.Type().createReference(OLD_CONNECTOR);

        List<CtInvocation<?>> sendCalls = new ArrayList<CtInvocation<?>>();
        List<CtInvocation<?>> handleCalls = new ArrayList<CtInvocation<?>>();
        List<CtConstructorCall<?>> connectorCalls = new ArrayList<CtConstructorCall<?>>();
        List<CtField<?>> connectorFields = new ArrayList<CtField<?>>();
        List<CtTypedElement<?>> connectorLocals = new ArrayList<CtTypedElement<?>>();

        for (CtInvocation<?> inv : launcher.getModel().getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))) {
            CtExecutableReference<?> exec = inv.getExecutable();
            if (exec == null || exec.getDeclaringType() == null) {
                continue;
            }
            String owner = exec.getDeclaringType().getQualifiedName();
            String name = exec.getSimpleName();
            if (OLD_SERVER.equals(owner) && ("setSendServerVersion".equals(name) || "setSendDateHeader".equals(name))) {
                sendCalls.add(inv);
            }
            if (OLD_HANDLER.equals(owner) && "handle".equals(name)) {
                handleCalls.add(inv);
            }
        }

        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class))) {
            if (call.getType() != null && OLD_CONNECTOR.equals(call.getType().getQualifiedName())) {
                connectorCalls.add(call);
            }
        }

        for (CtField<?> field : launcher.getModel().getElements(new TypeFilter<CtField<?>>(CtField.class))) {
            if (field.getType() != null && OLD_CONNECTOR.equals(field.getType().getQualifiedName())) {
                connectorFields.add(field);
            }
        }

        for (CtElement element : launcher.getModel().getElements(new TypeFilter<CtElement>(CtElement.class))) {
            if (element instanceof CtTypedElement) {
                CtTypedElement<?> local = (CtTypedElement<?>) element;
                if (local.getType() != null && OLD_CONNECTOR.equals(local.getType().getQualifiedName())) {
                    connectorLocals.add(local);
                }
            }
        }

        for (CtInvocation<?> inv : sendCalls) {
            CtStatement stmt = inv.getParent(CtStatement.class);
            if (stmt != null) {
                stmt.delete();
            }
        }

        for (CtInvocation<?> inv : handleCalls) {
            CtExecutableReference<?> exec = inv.getExecutable();
            if (exec == null) {
                continue;
            }
            CtMethod<?> method = inv.getParent(CtMethod.class);
            if (method != null && method.getParameters().size() == 4) {
                method.getParameters().get(1).setType(factory.Type().createReference("org.eclipse.jetty.server.Request"));
                method.getParameters().get(2).setType(factory.Type().createReference(NEW_HTTP_REQ));
                method.getParameters().get(3).setType(factory.Type().createReference(NEW_HTTP_RESP));
            }
            if (method == null) {
                continue;
            }
            for (Object paramObj : method.getParameters()) {
                CtTypedElement<?> param = (CtTypedElement<?>) paramObj;
                if (param.getType() != null && (OLD_HTTP_REQ.equals(param.getType().getQualifiedName()) || OLD_HTTP_RESP.equals(param.getType().getQualifiedName()) || OLD_SERVLET_EX.equals(param.getType().getQualifiedName()))) {
                    if (OLD_HTTP_REQ.equals(param.getType().getQualifiedName())) {
                        param.setType(factory.Type().createReference(NEW_HTTP_REQ));
                    } else if (OLD_HTTP_RESP.equals(param.getType().getQualifiedName())) {
                        param.setType(factory.Type().createReference(NEW_HTTP_RESP));
                    } else if (OLD_SERVLET_EX.equals(param.getType().getQualifiedName())) {
                        param.setType(factory.Type().createReference(NEW_SERVLET_EX));
                    }
                }
            }
        }

        for (CtConstructorCall<?> call : connectorCalls) {
            CtElement parent = call.getParent();
            if (parent instanceof CtStatement) {
                ((CtStatement) parent).replace(factory.Code().createCodeSnippetStatement("new " + NEW_CONNECTOR + "(server)"));
            } else {
                call.replace(factory.Code().createCodeSnippetExpression("new " + NEW_CONNECTOR + "(server)"));
            }
        }

        for (CtField<?> field : connectorFields) {
            field.setType(newConnectorRef);
        }

        for (CtTypedElement<?> local : connectorLocals) {
            local.setType(newConnectorRef);
        }

        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            if (type instanceof CtClass<?>) {
                fixHandleOverrides((CtClass<?>) type, factory);
            }
        }

        launcher.prettyprint();
    }

    private static void fixHandleOverrides(CtClass<?> type, Factory factory) {
        for (CtMethod<?> method : type.getMethods()) {
            if (!"handle".equals(method.getSimpleName()) || method.getParameters().size() != 4) {
                continue;
            }
            method.getParameters().get(1).setType(factory.Type().createReference("org.eclipse.jetty.server.Request"));
            method.getParameters().get(2).setType(factory.Type().createReference(NEW_HTTP_REQ));
            method.getParameters().get(3).setType(factory.Type().createReference(NEW_HTTP_RESP));
        }
    }
}
