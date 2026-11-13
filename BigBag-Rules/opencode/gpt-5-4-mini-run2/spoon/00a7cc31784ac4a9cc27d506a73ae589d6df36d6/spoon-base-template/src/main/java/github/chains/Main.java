package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {
    private static final String SSL_FILTER = "org.apache.mina.filter.ssl.SslFilter";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Path.of(args[0]);
        Path outputDir = args.length > 1 ? Path.of(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        rewriteSslFilterConstructors(launcher);
        removeObsoleteSslFilterCalls(launcher);
        removeObsoletePeerAddressHandoff(launcher);

        launcher.prettyprint();
    }

    private static void rewriteSslFilterConstructors(Launcher launcher) {
        Collection<CtConstructorCall<?>> calls = (Collection<CtConstructorCall<?>>) (Collection<?>) launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : calls) {
            if (isSslFilterType(call.getType()) && call.getArguments().size() == 2) {
                List<CtExpression<?>> arguments = new ArrayList<>(call.getArguments());
                arguments.remove(1);
                call.setArguments(arguments);
            }
        }
    }

    private static void removeObsoleteSslFilterCalls(Launcher launcher) {
        Collection<CtInvocation<?>> invocations = (Collection<CtInvocation<?>>) (Collection<?>) launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            String name = invocation.getExecutable().getSimpleName();
            if ("setUseClientMode".equals(name) || "initiateHandshake".equals(name)) {
                deleteEnclosingStatement(invocation);
            }
        }
    }

    private static void removeObsoletePeerAddressHandoff(Launcher launcher) {
        Collection<CtInvocation<?>> invocations = (Collection<CtInvocation<?>>) (Collection<?>) launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!"setAttribute".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().isEmpty()) {
                continue;
            }

            CtExpression<?> firstArgument = invocation.getArguments().get(0);
            if (containsPeerAddressReference(firstArgument)) {
                deleteEnclosingStatement(invocation);
            }
        }
    }

    private static boolean containsPeerAddressReference(CtElement element) {
        if (element == null) {
            return false;
        }
        return element.toString().contains("PEER_ADDRESS");
    }

    private static void deleteEnclosingStatement(CtElement element) {
        CtStatement statement = element.getParent(CtStatement.class);
        if (statement != null) {
            statement.delete();
        } else {
            element.delete();
        }
    }

    private static boolean isSslFilterType(CtTypeReference<?> typeReference) {
        if (typeReference == null) {
            return false;
        }
        String qualifiedName = typeReference.getQualifiedName();
        return SSL_FILTER.equals(qualifiedName) || "SslFilter".equals(typeReference.getSimpleName());
    }
}
