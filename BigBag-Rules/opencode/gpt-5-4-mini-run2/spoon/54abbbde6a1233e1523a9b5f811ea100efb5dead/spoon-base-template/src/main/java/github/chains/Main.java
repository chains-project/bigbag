package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = new HashMap<>();

    static {
        CONSTANTS.put("SEVEN", 7);
        CONSTANTS.put("TEN", 10);
        CONSTANTS.put("MILLION", 1_000_000);
    }

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                "Usage: Main <source-dir> [output-dir]"
            );
        }
        final Path source = Path.of(args[0]).toAbsolutePath().normalize();
        final Path target = args.length == 2
            ? Path.of(args[1]).toAbsolutePath().normalize()
            : source;
        final Path output;
        try {
            output = source.equals(target)
                ? Files.createTempDirectory("spoon-output")
                : target;
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        try (Stream<Path> walk = Files.walk(source)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/target/"))
                .filter(path -> !path.toString().contains("/src/test/"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        final var model = launcher.getModel();
        for (CtImport animport : model.getElements(new TypeFilter<>(CtImport.class))) {
            if (animport.getReference() instanceof CtTypeReference
                && OLD_TYPE.equals(
                    ((CtTypeReference<?>) animport.getReference()).getQualifiedName()
                )) {
                animport.delete();
            }
        }
        for (CtFieldAccess<?> access : model.getElements(new TypeFilter<>(CtFieldAccess.class))) {
            final CtFieldReference<?> field = access.getVariable();
            if (field == null || field.getDeclaringType() == null) {
                continue;
            }
            if (!OLD_TYPE.equals(field.getDeclaringType().getQualifiedName())) {
                continue;
            }
            final Integer replacement = CONSTANTS.get(field.getSimpleName());
            if (replacement != null) {
                access.replace(launcher.getFactory().createLiteral(replacement));
            }
        }
        launcher.prettyprint();
    }
}
