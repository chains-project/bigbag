package github.chains;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import spoon.Launcher;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String AUTH_SCHEME = "com.artipie.http.auth.AuthScheme";

    private static final String AUTHENTICATION = "com.artipie.http.auth.Authentication";

    private static final String ITERABLE = "java.lang.Iterable";

    private static final String STRING = "java.lang.String";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-directory> [output-directory]");
            System.exit(1);
        }
        final File sourceRoot = new File(args[0]);
        final File output = args.length > 1
            ? new File(args[1])
            : new File(sourceRoot, "spoon-out");
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        for (final Path path : listJavaFiles(sourceRoot.toPath())) {
            launcher.addInputResource(path.toString());
        }
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();
        transform(launcher);
        launcher.prettyprint();
    }

    private static List<Path> listJavaFiles(Path root) {
        try {
            return Files.walk(root)
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static void transform(Launcher launcher) {
        final List<CtMethod<?>> methods = new ArrayList<>();
        for (final CtElement element : launcher.getModel().getElements(e -> e instanceof CtMethod)) {
            methods.add((CtMethod<?>) element);
        }
        for (final CtMethod<?> method : methods) {
            if (isAuthSchemeAuthenticate(method)) {
                addChallengeParameter(method);
            }
            if (isAuthenticationUser(method)) {
                replaceAuthenticationPasswordType(method);
            }
        }
        for (final CtElement element : launcher.getModel().getElements(e -> e instanceof CtLambda)) {
            final CtLambda<?> lambda = (CtLambda<?>) element;
            if (isAuthSchemeLambda(lambda)) {
                addChallengeParameter(lambda);
            }
        }
        for (final CtElement element : launcher.getModel().getElements(e -> e instanceof CtInvocation)) {
            final CtInvocation<?> invocation = (CtInvocation<?>) element;
            if (isAuthSchemeInvocation(invocation)) {
                addMissingChallengeArgument(invocation);
            }
        }
    }

    private static boolean isAuthSchemeAuthenticate(CtMethod<?> method) {
        return "authenticate".equals(method.getSimpleName())
            && method.getParameters().size() == 1
            && isDeclaredBy(method, AUTH_SCHEME)
            && isType(method.getParameters().get(0).getType(), ITERABLE);
    }

    private static boolean isAuthenticationUser(CtMethod<?> method) {
        return "user".equals(method.getSimpleName())
            && method.getParameters().size() == 2
            && isDeclaredBy(method, AUTHENTICATION)
            && isType(method.getParameters().get(1).getType(), ITERABLE);
    }

    private static boolean isAuthSchemeLambda(CtLambda<?> lambda) {
        final CtTypeReference<?> type = lambda.getType();
        return type != null && isType(type, AUTH_SCHEME) && lambda.getParameters().size() == 1;
    }

    private static boolean isAuthSchemeInvocation(CtInvocation<?> invocation) {
        final CtExecutableReference<?> executable = invocation.getExecutable();
        return executable != null
            && "authenticate".equals(executable.getSimpleName())
            && invocation.getArguments().size() == 1
            && executable.getDeclaringType() != null
            && isType(executable.getDeclaringType(), AUTH_SCHEME);
    }

    private static void addChallengeParameter(CtMethod<?> method) {
        final CtParameter<String> parameter = method.getFactory().Core().createParameter();
        parameter.setSimpleName("challenge");
        parameter.setType(method.getFactory().Type().STRING);
        method.addParameter(parameter);
    }

    private static void addChallengeParameter(CtLambda<?> lambda) {
        final CtParameter<String> parameter = lambda.getFactory().Core().createParameter();
        parameter.setSimpleName("challenge");
        parameter.setType(lambda.getFactory().Type().STRING);
        lambda.addParameter(parameter);
    }

    private static void replaceAuthenticationPasswordType(CtMethod<?> method) {
        method.getParameters().get(1).setType(method.getFactory().Type().STRING);
    }

    private static void addMissingChallengeArgument(CtInvocation<?> invocation) {
        final CtLiteral<String> challenge = invocation.getFactory().Code().createLiteral("");
        invocation.addArgument(challenge);
    }

    private static boolean isDeclaredBy(CtMethod<?> method, String typeName) {
        final CtType<?> declaring = method.getDeclaringType();
        return declaring != null && isType(declaring.getReference(), typeName);
    }

    private static boolean isType(CtTypeReference<?> type, String typeName) {
        if (type == null) {
            return false;
        }
        final String qname = type.getQualifiedName();
        if (typeName.equals(qname)) {
            return true;
        }
        try {
            return type.isSubtypeOf(type.getFactory().Type().createReference(typeName));
        } catch (final Exception ignored) {
            return false;
        }
    }
}
