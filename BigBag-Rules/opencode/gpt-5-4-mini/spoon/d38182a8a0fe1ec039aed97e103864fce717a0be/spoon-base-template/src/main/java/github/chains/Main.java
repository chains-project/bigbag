package github.chains;

import java.util.List;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OLD_USER = "com.artipie.http.auth.Authentication.User";

    private static final String OLD_USER_DOLLAR = "com.artipie.http.auth.Authentication$User";

    private static final String NEW_USER = "com.artipie.http.auth.AuthUser";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: Main <input-src-dir> <output-src-dir>");
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new OldUserTypeProcessor());
        launcher.addProcessor(new OldUserConstructorProcessor());
        launcher.run();
    }

    private static final class OldUserTypeProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(final CtTypeReference<?> type) {
            return type != null && isOldUserType(type);
        }

        @Override
        public void process(final CtTypeReference<?> type) {
            final CtTypeReference<?> replacement = getFactory().Type().createReference(NEW_USER);
            type.replace(replacement);
        }
    }

    private static final class OldUserConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(final CtConstructorCall<?> call) {
            final CtTypeReference<?> type = call.getType();
            return type != null && isOldUserType(type);
        }

        @Override
        public void process(final CtConstructorCall<?> call) {
            call.setType(getFactory().Type().createReference(NEW_USER));
            final List<CtExpression<?>> args = call.getArguments();
            if (args.size() == 1) {
                call.addArgument(getFactory().Code().createLiteral(""));
            }
        }
    }

    private static boolean isOldUserType(final CtTypeReference<?> type) {
        final String qualified = type.getQualifiedName();
        if (OLD_USER.equals(qualified) || OLD_USER_DOLLAR.equals(qualified)) {
            return true;
        }
        return "User".equals(type.getSimpleName())
            && type.getDeclaringType() != null
            && "Authentication".equals(type.getDeclaringType().getSimpleName())
            && type.getDeclaringType().getPackage() != null
            && "com.artipie.http.auth".equals(type.getDeclaringType().getPackage().getQualifiedName());
    }
}
