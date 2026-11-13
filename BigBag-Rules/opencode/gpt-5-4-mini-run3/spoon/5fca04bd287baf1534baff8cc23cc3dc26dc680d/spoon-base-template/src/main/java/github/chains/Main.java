package github.chains;

import java.nio.file.Path;
import java.util.Collections;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OPTIONAL = "java.util.Optional";

    private static final String AUTH_USER = "com.artipie.http.auth.Authentication.User";

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }
        final Path input = Path.of(args[0]).toAbsolutePath().normalize();
        final Path output = args.length > 1
            ? Path.of(args[1]).toAbsolutePath().normalize()
            : input.resolveSibling(input.getFileName() + "-spoon-out");
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new AuthSchemeResultUserReturnTypeProcessor());
        launcher.addProcessor(new AuthSchemeResultUserConstructorProcessor());
        launcher.buildModel();
        launcher.prettyprint();
    }

    private static final class AuthSchemeResultUserReturnTypeProcessor extends AbstractProcessor<CtMethod<?>> {

        @Override
        public boolean isToBeProcessed(final CtMethod<?> method) {
            return "user".equals(method.getSimpleName())
                && method.getParameters().isEmpty()
                && isOptionalAuthenticationUser(method.getType());
        }

        @Override
        public void process(final CtMethod<?> method) {
            final CtTypeReference<?> type = method.getType();
            type.setActualTypeArguments(Collections.singletonList(optionalStringType(method)));
        }

        private static boolean isOptionalAuthenticationUser(final CtTypeReference<?> type) {
            return type != null
                && OPTIONAL.equals(type.getQualifiedName())
                && type.getActualTypeArguments().size() == 1
                && isAuthenticationUser(type.getActualTypeArguments().get(0));
        }

        private static boolean isAuthenticationUser(final CtTypeReference<?> type) {
            return type != null && AUTH_USER.equals(Main.qualifiedNestedName(type));
        }

        private CtTypeReference<?> optionalStringType(final CtMethod<?> method) {
            final spoon.reflect.factory.Factory factory = java.util.Objects.requireNonNull(
                method.getFactory(),
                "Factory not initialized"
            );
            final CtTypeReference<?> optional = factory.Type().createReference(OPTIONAL);
            optional.addActualTypeArgument(factory.Type().STRING);
            return optional;
        }
    }

    private static final class AuthSchemeResultUserConstructorProcessor
        extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(final CtConstructorCall<?> call) {
            final CtMethod<?> method = call.getParent(CtMethod.class);
            return method != null
                && "user".equals(method.getSimpleName())
                && Main.isOptionalAuthenticationUser(method.getType())
                && isAuthenticationUser(call.getType())
                && call.getArguments().size() == 1;
        }

        @Override
        public void process(final CtConstructorCall<?> call) {
            call.replace(call.getArguments().get(0).clone());
        }

        private static boolean isAuthenticationUser(final CtTypeReference<?> type) {
            return type != null && AUTH_USER.equals(Main.qualifiedNestedName(type));
        }
    }

    private static String qualifiedNestedName(final CtTypeReference<?> type) {
        final CtTypeReference<?> declaring = type.getDeclaringType();
        return declaring == null
            ? type.getQualifiedName()
            : declaring.getQualifiedName() + "." + type.getSimpleName();
    }

    private static boolean isOptionalAuthenticationUser(final CtTypeReference<?> type) {
        return type != null
            && OPTIONAL.equals(type.getQualifiedName())
            && type.getActualTypeArguments().size() == 1
            && isAuthenticationUser(type.getActualTypeArguments().get(0));
    }

    private static boolean isAuthenticationUser(final CtTypeReference<?> type) {
        return type != null && AUTH_USER.equals(qualifiedNestedName(type));
    }
}
