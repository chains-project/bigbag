package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = numbers();

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-dir> [output-dir]");
        }
        final Path input = Path.of(args[0]);
        final Path output = args.length == 2 ? Path.of(args[1]) : input.resolveSibling(input.getFileName() + "-fixed");
        final Launcher launcher = new Launcher();
        addJavaSources(launcher, input);
        launcher.setSourceOutputDirectory(output.toString());
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addProcessor(new TvConstantProcessor());
        launcher.run();
    }

    private static void addJavaSources(final Launcher launcher, final Path input) {
        try {
            Files.walk(input)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to read input sources", err);
        }
    }

    private static Map<String, Integer> numbers() {
        final Map<String, Integer> map = new HashMap<>();
        map.put("ZERO", 0);
        map.put("ONE", 1);
        map.put("TWO", 2);
        map.put("THREE", 3);
        map.put("FOUR", 4);
        map.put("FIVE", 5);
        map.put("SIX", 6);
        map.put("SEVEN", 7);
        map.put("EIGHT", 8);
        map.put("NINE", 9);
        map.put("TEN", 10);
        map.put("ELEVEN", 11);
        map.put("TWELVE", 12);
        map.put("THIRTEEN", 13);
        map.put("FOURTEEN", 14);
        map.put("FIFTEEN", 15);
        map.put("SIXTEEN", 16);
        map.put("SEVENTEEN", 17);
        map.put("EIGHTEEN", 18);
        map.put("NINETEEN", 19);
        map.put("TWENTY", 20);
        return map;
    }

    private static final class TvConstantProcessor extends AbstractProcessor<CtFieldAccess<?>> {

        @Override
        public void process(final CtFieldAccess<?> access) {
            if (!this.isOldTvAccess(access)) {
                return;
            }
            final String name = access.getVariable().getSimpleName();
            final Integer value = CONSTANTS.get(name);
            if (value == null) {
                return;
            }
            final CtLiteral<Integer> literal = access.getFactory().createLiteral(value);
            access.replace(literal);
        }

        private boolean isOldTvAccess(final CtFieldAccess<?> access) {
            final CtTypeReference<?> type = access.getVariable().getDeclaringType();
            if (type != null && OLD_TYPE.equals(type.getQualifiedName())) {
                return true;
            }
            final Object target = access.getTarget();
            if (target instanceof CtTypeAccess<?>) {
                final CtTypeReference<?> targetType = ((CtTypeAccess<?>) target).getAccessedType();
                return targetType != null && (
                    OLD_TYPE.equals(targetType.getQualifiedName())
                        || "Tv".equals(targetType.getSimpleName())
                );
            }
            final String repr = access.toString();
            return repr.equals("Tv." + access.getVariable().getSimpleName())
                || repr.endsWith("Tv." + access.getVariable().getSimpleName())
                || repr.contains("com.jcabi.aspects.Tv");
        }
    }
}
