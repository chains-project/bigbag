package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtFieldReference;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = createConstants();

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                "Usage: Main <input-source-dir> [output-source-dir]"
            );
        }
        final File input = new File(args[0]);
        final File output = args.length == 2 ? new File(args[1]) : input;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setPreserveLineNumbers(false);
        for (final String source : sources(input.toPath())) {
            launcher.addInputResource(source);
        }
        launcher.setSourceOutputDirectory(output);
        launcher.addProcessor(new TvProcessor());
        launcher.run();
    }

    private static List<String> sources(final Path root) {
        try {
            return Files.walk(root)
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .map(Path::toString)
                .collect(Collectors.toCollection(ArrayList::new));
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, Integer> createConstants() {
        final Map<String, Integer> map = new HashMap<>(32);
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
        map.put("THIRTY", 30);
        map.put("FORTY", 40);
        map.put("FIFTY", 50);
        map.put("SIXTY", 60);
        map.put("SEVENTY", 70);
        map.put("EIGHTY", 80);
        map.put("NINETY", 90);
        map.put("HUNDRED", 100);
        map.put("THOUSAND", 1000);
        map.put("MILLION", 1000000);
        map.put("BILLION", 1000000000);
        return map;
    }

    private static final class TvProcessor extends AbstractProcessor<CtFieldAccess<?>> {

        @Override
        public void process(final CtFieldAccess<?> access) {
            final CtFieldReference<?> field = access.getVariable();
            if (field == null || field.getDeclaringType() == null) {
                return;
            }
            if (!OLD_TYPE.equals(field.getDeclaringType().getQualifiedName())) {
                return;
            }
            final Integer value = CONSTANTS.get(field.getSimpleName());
            if (value == null) {
                throw new IllegalStateException(
                    String.format("Unsupported Tv constant: %s", field.getSimpleName())
                );
            }
            final CtLiteral<Integer> literal = access.getFactory().createLiteral(value);
            access.replace(literal);
        }
    }
}
