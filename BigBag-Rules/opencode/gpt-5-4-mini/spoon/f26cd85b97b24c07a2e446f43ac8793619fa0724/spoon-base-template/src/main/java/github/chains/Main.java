package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public class Main {

    private static final String OLD_SELECT_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_REQUEST = "javax.servlet.http.HttpServletRequest";
    private static final String OLD_SERVLET_RESPONSE = "javax.servlet.http.HttpServletResponse";
    private static final String OLD_SERVLET_EXCEPTION = "javax.servlet.ServletException";
    private static final String NEW_SERVLET_REQUEST = "jakarta.servlet.http.HttpServletRequest";
    private static final String NEW_SERVLET_RESPONSE = "jakarta.servlet.http.HttpServletResponse";
    private static final String NEW_SERVLET_EXCEPTION = "jakarta.servlet.ServletException";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        File sourceDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : new File(sourceDir.getParentFile(), sourceDir.getName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setShouldCompile(true);
        launcher.addInputResource(sourceDir.getAbsolutePath());
        launcher.getEnvironment().setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        migrateReferences(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        migrateJettyConnectors(launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));

        launcher.prettyprint();
    }

    private static void migrateReferences(List<CtTypeReference<?>> refs) {
        for (CtTypeReference<?> ref : refs) {
            String qn = ref.getQualifiedName();
            if (OLD_SELECT_CONNECTOR.equals(qn)) {
                ref.setSimpleName(simpleName(NEW_SERVER_CONNECTOR));
                ref.setPackage(ref.getFactory().Package().createReference(packageName(NEW_SERVER_CONNECTOR)));
            } else if (qn.startsWith("javax.servlet")) {
                String migrated = qn.replace("javax.servlet", "jakarta.servlet");
                ref.setSimpleName(simpleName(migrated));
                ref.setPackage(ref.getFactory().Package().createReference(packageName(migrated)));
            }
        }
    }

    private static void migrateJettyConnectors(List<CtConstructorCall<?>> calls) {
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !OLD_SELECT_CONNECTOR.equals(type.getQualifiedName())) {
                continue;
            }

            CtInvocation<?> addConnectorCall = findConnectorRegistration(call);
            if (addConnectorCall == null || addConnectorCall.getTarget() == null) {
                continue;
            }

            CtConstructorCall<?> replacement = call.getFactory().Code().createConstructorCall(
                    call.getFactory().Type().createReference(NEW_SERVER_CONNECTOR),
                    addConnectorCall.getTarget().clone());
            call.replace(replacement);
        }
    }

    private static CtInvocation<?> findConnectorRegistration(CtConstructorCall<?> call) {
        CtStatement statement = call.getParent(CtStatement.class);
        if (statement == null) {
            return null;
        }

        CtType<?> owner = call.getParent(CtType.class);
        if (owner == null) {
            return null;
        }

        String connectorName = assignedVariableName(statement);
        if (connectorName == null) {
            return null;
        }

        for (CtInvocation<?> invocation : owner.getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || !"addConnector".equals(executable.getSimpleName()) || invocation.getArguments().isEmpty()) {
                continue;
            }

            Object arg = invocation.getArguments().get(0);
            if (arg instanceof spoon.reflect.code.CtVariableRead) {
                spoon.reflect.code.CtVariableRead<?> read = (spoon.reflect.code.CtVariableRead<?>) arg;
                if (connectorName.equals(read.getVariable().getSimpleName())) {
                    return invocation;
                }
            }
        }

        return null;
    }

    private static String assignedVariableName(CtStatement statement) {
        if (statement instanceof CtLocalVariable) {
            return ((CtLocalVariable<?>) statement).getSimpleName();
        }
        if (statement instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) statement;
            if (assignment.getAssigned() != null) {
                return assignment.getAssigned().toString().replace("this.", "");
            }
        }
        return null;
    }

    private static String packageName(String qn) {
        int idx = qn.lastIndexOf('.');
        return idx < 0 ? "" : qn.substring(0, idx);
    }

    private static String simpleName(String qn) {
        int idx = qn.lastIndexOf('.');
        return idx < 0 ? qn : qn.substring(idx + 1);
    }
}
