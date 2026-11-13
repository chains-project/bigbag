package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String SOURCE_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> REPLACEMENTS = Map.of(
        "FIVE", 5,
        "HUNDRED", 100,
        "THOUSAND", 1000
    );

    private Main() {
        // no-op
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                "Usage: Main <input-source-dir> <output-source-dir>"
            );
        }
        final File input = new File(args[0]);
        final File output = new File(args[1]);
        final Launcher launcher = new Launcher();
        addJavaSources(launcher, input.toPath());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setSourceOutputDirectory(output);
        launcher.buildModel();
        final Factory factory = launcher.getFactory();
        for (final CtFieldAccess<?> access : launcher.getModel().getElements(
            new TypeFilter<>(CtFieldAccess.class)
        )) {
            if (isTargetAccess(access)) {
                replaceWithLiteral(factory, access);
            }
        }
        for (final CtImport imp : launcher.getModel().getElements(
            new TypeFilter<>(CtImport.class)
        )) {
            removeDeprecatedImport(imp);
        }
        launcher.prettyprint();
        stripLegacyImports(output.toPath());
    }

    private static void addJavaSources(
        final Launcher launcher,
        final Path root
    ) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static boolean isTargetAccess(final CtFieldAccess<?> access) {
        final CtTypeReference<?> type = access.getVariable().getDeclaringType();
        return type != null && SOURCE_TYPE.equals(type.getQualifiedName())
            && REPLACEMENTS.containsKey(access.getVariable().getSimpleName());
    }

    private static void replaceWithLiteral(
        final Factory factory,
        final CtElement element
    ) {
        final CtFieldAccess<?> access = (CtFieldAccess<?>) element;
        final String name = access.getVariable().getSimpleName();
        final CtLiteral<Integer> literal = factory.createLiteral(
            Objects.requireNonNull(REPLACEMENTS.get(name))
        );
        access.replace(literal);
    }

    private static void removeDeprecatedImport(final CtImport imp) {
        final Object reference = imp.getReference();
        if (reference instanceof CtTypeReference
            && SOURCE_TYPE.equals(((CtTypeReference<?>) reference).getQualifiedName())) {
            imp.delete();
        }
    }

    private static void stripLegacyImports(final Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::stripLegacyImport);
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static void stripLegacyImport(final Path path) {
        try {
            final String importLine = "import " + SOURCE_TYPE + ";";
            final String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.contains(importLine)) {
                Files.writeString(
                    path,
                    content.replace(importLine + System.lineSeparator(), "")
                        .replace(importLine + "\n", "")
                        .replace(importLine + "\r\n", ""),
                    StandardCharsets.UTF_8
                );
            }
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }
}
