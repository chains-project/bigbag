package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_USER = "com.artipie.http.auth.Authentication.User";

    private static final String NEW_USER = "com.artipie.http.auth.AuthUser";

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected at least one argument: source directory");
        }
        final File input = new File(args[0]);
        final File output = args.length > 1 ? new File(args[1]) : input;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(17);
        addJavaSources(launcher, input.toPath());
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();
        final Factory factory = launcher.getFactory();
        final CtTypeReference<?> replacement = factory.Type().createReference(NEW_USER);
        for (CtTypeReference<?> ref : launcher.getModel().getElements(
            new TypeFilter<>(CtTypeReference.class)
        )) {
            if (OLD_USER.equals(ref.getQualifiedName())) {
                ref.replace(replacement.clone());
            }
        }
        for (CtConstructorCall<?> call : launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        )) {
            final CtTypeReference<?> type = call.getType();
            if (type != null && OLD_USER.equals(type.getQualifiedName())) {
                call.replace(rewriteConstructor(factory, call.getArguments()));
            }
        }
        launcher.prettyprint();
    }

    private static void addJavaSources(final Launcher launcher, final Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .filter(path -> !path.toString().contains(File.separator + "target" + File.separator))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to scan sources", err);
        }
    }

    private static CtConstructorCall<?> rewriteConstructor(
        final Factory factory,
        final List<CtExpression<?>> arguments
    ) {
        final CtConstructorCall<?> call = factory.Code().createConstructorCall(
            factory.Type().createReference(NEW_USER),
            arguments.toArray(new CtExpression<?>[0])
        );
        if (arguments.size() == 1) {
            call.addArgument(factory.Code().createLiteral(""));
        }
        return call;
    }
}
