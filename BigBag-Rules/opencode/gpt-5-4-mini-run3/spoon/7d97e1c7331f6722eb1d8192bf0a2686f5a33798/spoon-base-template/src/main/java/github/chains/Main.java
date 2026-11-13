package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = new HashMap<>();

    static {
        CONSTANTS.put("THREE", 3);
        CONSTANTS.put("FOUR", 4);
        CONSTANTS.put("FIVE", 5);
        CONSTANTS.put("SIX", 6);
        CONSTANTS.put("SEVEN", 7);
        CONSTANTS.put("EIGHT", 8);
        CONSTANTS.put("NINE", 9);
        CONSTANTS.put("TEN", 10);
        CONSTANTS.put("FIFTEEN", 15);
        CONSTANTS.put("TWENTY", 20);
        CONSTANTS.put("THIRTY", 30);
        CONSTANTS.put("FORTY", 40);
        CONSTANTS.put("FIFTY", 50);
        CONSTANTS.put("SIXTY", 60);
        CONSTANTS.put("SEVENTY", 70);
        CONSTANTS.put("EIGHTY", 80);
        CONSTANTS.put("NINETY", 90);
        CONSTANTS.put("HUNDRED", 100);
        CONSTANTS.put("THOUSAND", 1000);
        CONSTANTS.put("MILLION", 1000000);
        CONSTANTS.put("BILLION", 1000000000);
    }

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                "Usage: Main <input-source-dir> <output-source-dir>"
            );
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new TvCompilationUnitProcessor());
        launcher.addProcessor(new TvAccessProcessor());
        launcher.run();
        cleanupLeftoverTvImports(Path.of(args[1]));
    }

    private static void cleanupLeftoverTvImports(final Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(Main::removeTvImportLine);
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static void removeTvImportLine(final Path path) {
        try {
            final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            final List<String> kept = new ArrayList<>(lines.size());
            for (final String line : lines) {
                if (line.trim().equals("import com.jcabi.aspects.Tv;")) {
                    continue;
                }
                kept.add(line);
            }
            Files.write(path, kept, StandardCharsets.UTF_8);
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static final class TvCompilationUnitProcessor extends AbstractProcessor<CtCompilationUnit> {
        @Override
        public void process(final CtCompilationUnit unit) {
            final List<CtImport> kept = new ArrayList<>();
            for (final CtImport element : new ArrayList<>(unit.getImports())) {
                if (isOldTvImport(element)) {
                    continue;
                }
                kept.add(element);
            }
            unit.setImports(kept);
        }

        private static boolean isOldTvImport(final CtImport element) {
            if (element.toString().contains(OLD_TYPE)) {
                return true;
            }
            final CtTypeReference<?> type = importType(element);
            if (type != null && OLD_TYPE.equals(type.getQualifiedName())) {
                return true;
            }
            final CtFieldReference<?> field = importField(element);
            if (field != null) {
                final CtTypeReference<?> declaringType = field.getDeclaringType();
                if (declaringType != null && OLD_TYPE.equals(declaringType.getQualifiedName())) {
                    return true;
                }
            }
            return false;
        }

        private static CtTypeReference<?> importType(final CtImport element) {
            if (element.getReference() instanceof CtTypeReference<?>) {
                return (CtTypeReference<?>) element.getReference();
            }
            return null;
        }

        private static CtFieldReference<?> importField(final CtImport element) {
            if (element.getReference() instanceof CtFieldReference<?>) {
                return (CtFieldReference<?>) element.getReference();
            }
            return null;
        }
    }

    private static final class TvAccessProcessor extends AbstractProcessor<CtFieldAccess<?>> {
        @Override
        public void process(final CtFieldAccess<?> access) {
            if (!isTvAccess(access)) {
                return;
            }
            final String name = access.getVariable().getSimpleName();
            final Integer value = CONSTANTS.get(name);
            if (value != null) {
                access.replace(access.getFactory().Code().createLiteral(value));
            }
        }

        private static boolean isTvAccess(final CtFieldAccess<?> access) {
            final CtFieldReference<?> variable = access.getVariable();
            if (variable != null) {
                final CtTypeReference<?> declaring = variable.getDeclaringType();
                if (declaring != null && OLD_TYPE.equals(declaring.getQualifiedName())) {
                    return true;
                }
            }
            if (access.getTarget() != null
                && access.getTarget().getType() != null
                && OLD_TYPE.equals(access.getTarget().getType().getQualifiedName())) {
                return true;
            }
            return false;
        }
    }
}
