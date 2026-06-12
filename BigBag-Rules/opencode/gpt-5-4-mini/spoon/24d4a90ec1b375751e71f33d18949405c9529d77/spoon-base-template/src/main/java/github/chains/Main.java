package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = Map.of(
        "FIVE", 5,
        "HUNDRED", 100,
        "THOUSAND", 1000
    );

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                "Usage: Main <input-source-dir> [output-source-dir]"
            );
        }
        final Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        for (final Path file : sourceFiles(input)) {
            launcher.addInputResource(file.toString());
        }
        final File output = new File(args.length == 2 ? args[1] : args[0] + "-spoon-out");
        launcher.setSourceOutputDirectory(output);
        launcher.addProcessor(new TvConstantProcessor());
        launcher.run();
        cleanupImports(output.toPath());
    }

    private static List<Path> sourceFiles(final Path input) {
        final List<Path> roots = new ArrayList<>();
        if (Files.isDirectory(input)) {
            if (isJavaSourceRoot(input)) {
                addJavaFiles(input, roots);
            } else {
                final Path main = input.resolve("main/java");
                final Path test = input.resolve("test/java");
                if (Files.isDirectory(main)) {
                    addJavaFiles(main, roots);
                }
                if (Files.isDirectory(test)) {
                    addJavaFiles(test, roots);
                }
            }
        }
        roots.sort(Comparator.naturalOrder());
        return roots;
    }

    private static void addJavaFiles(final Path root, final List<Path> files) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !"package-info.java".equals(path.getFileName().toString()))
                .forEach(files::add);
        } catch (final java.io.IOException err) {
            throw new IllegalStateException("Failed to scan source files under " + root, err);
        }
    }

    private static boolean isJavaSourceRoot(final Path input) {
        final Path parent = input.getParent();
        final Path grandparent = parent == null ? null : parent.getParent();
        return parent != null
            && grandparent != null
            && "java".equals(input.getFileName().toString())
            && ("main".equals(parent.getFileName().toString()) || "test".equals(parent.getFileName().toString()))
            && "src".equals(grandparent.getFileName().toString());
    }

    private static void cleanupImports(final Path output) {
        try (Stream<Path> walk = Files.walk(output)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::removeOldTypeImport);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to clean generated sources under " + output, err);
        }
    }

    private static void removeOldTypeImport(final Path file) {
        try {
            final String text = Files.readString(file)
                .replace("import com.jcabi.aspects.Tv;\r\n", "")
                .replace("import com.jcabi.aspects.Tv;\n", "")
                .replace("import com.jcabi.aspects.Tv;", "");
            Files.writeString(file, text);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to remove obsolete import from " + file, err);
        }
    }

    private static final class TvConstantProcessor extends AbstractProcessor<CtFieldRead<?>> {

        @Override
        public boolean isToBeProcessed(final CtFieldRead<?> element) {
            return constantValue(element) != null;
        }

        @Override
        public void process(final CtFieldRead<?> element) {
            final Integer value = constantValue(element);
            if (value == null) {
                return;
            }
            final CtLiteral<Integer> literal = getFactory().Code().createLiteral(value);
            element.replace(literal);
        }

        private static Integer constantValue(final CtFieldRead<?> element) {
            final CtFieldReference<?> variable = element.getVariable();
            if (variable == null) {
                return null;
            }
            final CtTypeReference<?> declaring = variable.getDeclaringType();
            if (declaring == null || !OLD_TYPE.equals(declaring.getQualifiedName())) {
                return null;
            }
            return CONSTANTS.get(variable.getSimpleName());
        }
    }

}
