package github.chains;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OLD_SLICE_AUTH = "com.artipie.http.auth.SliceAuth";

    private static final String NEW_BASIC_AUTH_SLICE = "com.artipie.http.auth.BasicAuthSlice";

    private static final String OLD_BASIC_IDENTITIES = "com.artipie.http.auth.BasicIdentities";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> <output-dir>");
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setSourceOutputDirectory(new File(args[1]));
        for (final Path root : sourceRoots(Paths.get(args[0]))) {
            launcher.addInputResource(root.toString());
        }
        launcher.addProcessor(new AuthMigrationProcessor(launcher.getFactory()));
        launcher.run();
    }

    private static List<Path> sourceRoots(final Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            final List<Path> files = walk
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .collect(Collectors.toList());
            if (files.isEmpty()) {
                final List<Path> roots = new ArrayList<>();
                roots.add(root);
                return roots;
            }
            return files;
        } catch (final java.io.IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static final class AuthMigrationProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        private final Factory factory;

        AuthMigrationProcessor(final Factory factory) {
            this.factory = factory;
        }

        @Override
        public void process(final CtConstructorCall<?> call) {
            final String type = qualifiedName(call.getType());
            if (OLD_SLICE_AUTH.equals(type)) {
                this.replaceSliceAuth(call);
            } else if (OLD_BASIC_IDENTITIES.equals(type)) {
                this.unwrapBasicIdentities(call);
            }
        }

        private void replaceSliceAuth(final CtConstructorCall<?> call) {
            if (call.getArguments().size() != 3) {
                return;
            }
            final List<CtExpression<?>> args = new ArrayList<>(call.getArguments());
            final CtExpression<?> origin = args.get(0);
            final CtExpression<?> permission = args.get(1);
            final CtExpression<?> authentication = this.unwrapIdentities(args.get(2));
            call.setType(this.type(NEW_BASIC_AUTH_SLICE));
            call.setArguments(new ArrayList<>(java.util.Arrays.asList(origin, authentication, permission)));
        }

        private void unwrapBasicIdentities(final CtConstructorCall<?> call) {
            if (call.getArguments().size() == 1) {
                call.replace(this.unwrapIdentities(call.getArguments().get(0)));
            }
        }

        private CtExpression<?> unwrapIdentities(final CtExpression<?> expr) {
            if (expr instanceof CtConstructorCall<?>) {
                final CtConstructorCall<?> call = (CtConstructorCall<?>) expr;
                if (OLD_BASIC_IDENTITIES.equals(qualifiedName(call.getType())) && !call.getArguments().isEmpty()) {
                    return this.unwrapIdentities(call.getArguments().get(0));
                }
            }
            return expr;
        }

        private CtTypeReference<?> type(final String name) {
            return this.factory.Type().createReference(name);
        }

        private static String qualifiedName(final CtTypeReference<?> ref) {
            return ref == null ? null : ref.getQualifiedName();
        }
    }
}
