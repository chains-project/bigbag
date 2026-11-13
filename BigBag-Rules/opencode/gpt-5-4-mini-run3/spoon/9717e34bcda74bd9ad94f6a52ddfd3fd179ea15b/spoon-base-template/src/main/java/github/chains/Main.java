package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> TV_VALUES = Map.ofEntries(
        Map.entry("ZERO", 0),
        Map.entry("ONE", 1),
        Map.entry("TWO", 2),
        Map.entry("THREE", 3),
        Map.entry("FOUR", 4),
        Map.entry("FIVE", 5),
        Map.entry("SIX", 6),
        Map.entry("SEVEN", 7),
        Map.entry("EIGHT", 8),
        Map.entry("NINE", 9),
        Map.entry("TEN", 10),
        Map.entry("ELEVEN", 11),
        Map.entry("TWELVE", 12),
        Map.entry("THIRTEEN", 13),
        Map.entry("FOURTEEN", 14),
        Map.entry("FIFTEEN", 15),
        Map.entry("SIXTEEN", 16),
        Map.entry("SEVENTEEN", 17),
        Map.entry("EIGHTEEN", 18),
        Map.entry("NINETEEN", 19),
        Map.entry("TWENTY", 20),
        Map.entry("THIRTY", 30),
        Map.entry("FORTY", 40),
        Map.entry("FIFTY", 50),
        Map.entry("SIXTY", 60),
        Map.entry("SEVENTY", 70),
        Map.entry("EIGHTY", 80),
        Map.entry("NINETY", 90),
        Map.entry("HUNDRED", 100),
        Map.entry("THOUSAND", 1_000),
        Map.entry("MILLION", 1_000_000),
        Map.entry("BILLION", 1_000_000_000)
    );

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory");
        }
        final Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path output = args.length > 1
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : input;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        final List<CtFieldAccess<?>> accesses = new ArrayList<>(
            launcher.getModel().getElements(new TypeFilter<>(CtFieldAccess.class))
        );
        for (final CtFieldAccess<?> access : accesses) {
            final CtFieldReference<?> variable = access.getVariable();
            if (variable == null) {
                continue;
            }
            final CtTypeReference<?> declaring = variable.getDeclaringType();
            if (declaring == null || !OLD_TYPE.equals(declaring.getQualifiedName())) {
                continue;
            }
            final Integer value = tvValue(variable.getSimpleName());
            if (value == null) {
                continue;
            }
            access.replace(factory.createLiteral(value));
        }
        launcher.prettyprint();
    }

    private static Integer tvValue(final String name) {
        return TV_VALUES.get(name);
    }
}
