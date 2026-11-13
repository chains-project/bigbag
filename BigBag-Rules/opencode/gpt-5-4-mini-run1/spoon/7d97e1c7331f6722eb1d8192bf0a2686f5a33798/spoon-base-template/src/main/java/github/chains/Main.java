package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> VALUES = numbers();

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }
        final Path input = Paths.get(args[0]).toAbsolutePath();
        final Path output = args.length > 1 ? Paths.get(args[1]).toAbsolutePath() : input;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        try {
            Files.walk(input)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
        try {
            Files.createDirectories(output);
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        final Factory factory = launcher.getFactory();
        for (final CtCompilationUnit unit : launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class))) {
            unit.getImports().removeIf(Main::isLegacyTvImport);
        }
        for (final CtFieldRead<?> read : launcher.getModel().getElements(new TypeFilter<>(CtFieldRead.class))) {
            final CtFieldReference<?> reference = read.getVariable();
            if (isLegacyTv(reference) || isLegacyTvText(read)) {
                final Integer value = VALUES.get(reference == null ? null : reference.getSimpleName());
                if (value != null) {
                    final CtLiteral<Integer> literal = factory.createLiteral(value);
                    read.replace(literal);
                }
            }
        }
        launcher.prettyprint();
    }

    private static boolean isLegacyTvImport(final CtImport imp) {
        if (imp == null || imp.getReference() == null) {
            return false;
        }
        final String qualified = imp.getReference().toString();
        return OLD_TYPE.equals(qualified) || "Tv".equals(imp.getReference().getSimpleName());
    }

    private static boolean isLegacyTv(final CtFieldReference<?> reference) {
        if (reference == null) {
            return false;
        }
        final CtTypeReference<?> type = reference.getDeclaringType();
        if (type == null) {
            return false;
        }
        return OLD_TYPE.equals(type.getQualifiedName()) || "Tv".equals(type.getSimpleName());
    }

    private static boolean isLegacyTvText(final CtFieldAccess<?> access) {
        final String text = access == null ? "" : access.toString();
        return text.startsWith("Tv.") || text.startsWith("com.jcabi.aspects.Tv.");
    }

    private static Map<String, Integer> numbers() {
        final Map<String, Integer> map = new LinkedHashMap<>();
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
        return map;
    }
}
