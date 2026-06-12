package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String SSL_FILTER_FQN = "org.apache.mina.filter.ssl.SslFilter";
    private static final String SSL_CONTEXT_FQN = "javax.net.ssl.SSLContext";
    private static final String SET_USE_CLIENT_MODE = "setUseClientMode";
    private static final String INITIATE_HANDSHAKE = "initiateHandshake";
    private static final String GET_SSL_SESSION = "getSslSession";
    private static final String PEER_ADDRESS = "PEER_ADDRESS";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one source directory path");
        }

        Path sourceDirectory = Paths.get(args[0]).toAbsolutePath().normalize();

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setSourceOutputDirectory(sourceDirectory.toFile());
        launcher.addInputResource(sourceDirectory.toString());

        launcher.buildModel();
        CtModel model = launcher.getModel();

        int changes = 0;
        changes += fixSslFilterConstructors(model);
        changes += removeRemovedSslFilterInvocations(model);
        changes += removePeerAddressUses(model);
        changes += replaceGetSslSessionCalls(model);

        if (changes > 0) {
            launcher.prettyprint();
        }
    }

    private static int fixSslFilterConstructors(CtModel model) {
        int changes = 0;
        List<CtConstructorCall> constructorCalls = new ArrayList<>(model.getElements(new TypeFilter<>(CtConstructorCall.class)));
        for (CtConstructorCall<?> call : constructorCalls) {
            if (!isSslFilterConstructor(call) || call.getArguments().size() != 2) {
                continue;
            }

            call.setArguments(List.of(call.getArguments().get(0).clone()));
            changes++;
        }
        return changes;
    }

    private static int removeRemovedSslFilterInvocations(CtModel model) {
        int changes = 0;
        List<CtInvocation> invocations = new ArrayList<>(model.getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            if (!isRemovedSslFilterInvocation(invocation)) {
                continue;
            }

            removeStatement(invocation);
            changes++;
        }
        return changes;
    }

    private static int removePeerAddressUses(CtModel model) {
        int changes = 0;
        List<CtFieldAccess> fieldAccesses = new ArrayList<>(model.getElements(new TypeFilter<>(CtFieldAccess.class)));
        for (CtFieldAccess<?> fieldAccess : fieldAccesses) {
            if (!PEER_ADDRESS.equals(fieldAccess.getVariable().getSimpleName())) {
                continue;
            }

            CtInvocation<?> parentInvocation = fieldAccess.getParent(CtInvocation.class);
            if (parentInvocation != null && "setAttribute".equals(parentInvocation.getExecutable().getSimpleName())) {
                removeStatement(parentInvocation);
                changes++;
                continue;
            }

            removeElement(fieldAccess);
            changes++;
        }
        return changes;
    }

    private static int replaceGetSslSessionCalls(CtModel model) {
        int changes = 0;
        List<CtInvocation> invocations = new ArrayList<>(model.getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            if (!GET_SSL_SESSION.equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().size() != 1) {
                continue;
            }

            CtTypeReference<?> targetType = invocation.getTarget() != null ? invocation.getTarget().getType() : null;
            if (!isSslFilterType(targetType)) {
                continue;
            }

            String sessionExpression = invocation.getArguments().get(0).toString();
            String replacement = "(javax.net.ssl.SSLSession) " + sessionExpression + ".getAttribute(org.apache.mina.filter.ssl.SslFilter.SSL_SECURED)";
            invocation.replace(invocation.getFactory().Code().createCodeSnippetExpression(replacement));
            changes++;
        }
        return changes;
    }

    private static boolean isSslFilterConstructor(CtConstructorCall<?> call) {
        CtTypeReference<?> type = call.getExecutable() != null ? call.getExecutable().getDeclaringType() : call.getType();
        return isSslFilterType(type)
                && call.getArguments().size() >= 1
                && isSslContextExpression(call.getArguments().get(0));
    }

    private static boolean isRemovedSslFilterInvocation(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (!SET_USE_CLIENT_MODE.equals(methodName) && !INITIATE_HANDSHAKE.equals(methodName)) {
            return false;
        }

        CtTypeReference<?> targetType = invocation.getTarget() != null ? invocation.getTarget().getType() : null;
        return isSslFilterType(targetType);
    }

    private static boolean isSslFilterType(CtTypeReference<?> type) {
        if (type == null) {
            return false;
        }

        String qualifiedName = type.getQualifiedName();
        return SSL_FILTER_FQN.equals(qualifiedName) || "SslFilter".equals(type.getSimpleName()) || qualifiedName.endsWith(".SslFilter");
    }

    private static boolean isSslContextExpression(CtStatement statement) {
        return isSslContextExpression((Object) statement);
    }

    private static boolean isSslContextExpression(Object element) {
        if (!(element instanceof spoon.reflect.code.CtExpression<?> expression)) {
            return false;
        }

        CtTypeReference<?> type = expression.getType();
        if (type == null) {
            return false;
        }

        String qualifiedName = type.getQualifiedName();
        return SSL_CONTEXT_FQN.equals(qualifiedName) || "SSLContext".equals(type.getSimpleName()) || qualifiedName.endsWith(".SSLContext");
    }

    private static void removeStatement(CtStatement statement) {
        CtStatement parentStatement = statement;
        while (parentStatement != null && !(parentStatement.getParent() instanceof spoon.reflect.declaration.CtType<?>)) {
            CtStatement candidate = parentStatement.getParent(CtStatement.class);
            if (candidate == null || candidate == parentStatement) {
                break;
            }
            parentStatement = candidate;
        }

        if (parentStatement.getParent() instanceof CtBlock<?> block) {
            block.getStatements().remove(parentStatement);
            return;
        }

        statement.delete();
    }

    private static void removeElement(CtElement element) {
        CtStatement enclosingStatement = element.getParent(CtStatement.class);
        if (enclosingStatement != null) {
            removeStatement(enclosingStatement);
            return;
        }

        element.delete();
    }
}
