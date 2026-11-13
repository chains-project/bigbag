package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String REMOVED_TYPE = "com.jcabi.aspects.Tv";
    private static final String REMOVED_SIMPLE = "Tv";

    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try {
            Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transform);
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(
                Files.readString(file, StandardCharsets.UTF_8)
            );
            if (rewrite(cu)) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    private static boolean rewrite(final CompilationUnit cu) {
        boolean changed = false;
        changed |= cu.getImports().removeIf(
            imp -> REMOVED_TYPE.equals(imp.getNameAsString())
                || REMOVED_SIMPLE.equals(imp.getNameAsString())
        );
        final List<FieldAccessExpr> fields = cu.findAll(FieldAccessExpr.class);
        for (final FieldAccessExpr field : fields) {
            if (isRemovedTypeAccess(field)) {
                final Integer value = parseNumber(field.getNameAsString());
                if (value != null) {
                    field.replace(new IntegerLiteralExpr(Integer.toString(value)));
                    changed = true;
                }
            }
        }
        final List<NameExpr> names = cu.findAll(NameExpr.class);
        for (final NameExpr name : names) {
            if (hasStaticImportFromRemovedType(cu, name.getNameAsString())) {
                final Integer value = parseNumber(name.getNameAsString());
                if (value != null) {
                    name.replace(new IntegerLiteralExpr(Integer.toString(value)));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean isRemovedTypeAccess(final FieldAccessExpr field) {
        return field.getScope().isNameExpr()
            && REMOVED_SIMPLE.equals(field.getScope().asNameExpr().getNameAsString());
    }

    private static boolean hasStaticImportFromRemovedType(
        final CompilationUnit cu, final String constant
    ) {
        return cu.getImports().stream().anyMatch(
            imp -> imp.isStatic()
                && imp.getNameAsString().equals(REMOVED_TYPE)
                && (imp.isAsterisk() || imp.getName().getIdentifier().equals(constant))
        );
    }

    private static Integer parseNumber(final String constant) {
        final Map<String, Integer> units = new HashMap<>(32);
        units.put("ZERO", 0);
        units.put("ONE", 1);
        units.put("TWO", 2);
        units.put("THREE", 3);
        units.put("FOUR", 4);
        units.put("FIVE", 5);
        units.put("SIX", 6);
        units.put("SEVEN", 7);
        units.put("EIGHT", 8);
        units.put("NINE", 9);
        units.put("TEN", 10);
        units.put("ELEVEN", 11);
        units.put("TWELVE", 12);
        units.put("THIRTEEN", 13);
        units.put("FOURTEEN", 14);
        units.put("FIFTEEN", 15);
        units.put("SIXTEEN", 16);
        units.put("SEVENTEEN", 17);
        units.put("EIGHTEEN", 18);
        units.put("NINETEEN", 19);
        final Map<String, Integer> tens = new HashMap<>(8);
        tens.put("TWENTY", 20);
        tens.put("THIRTY", 30);
        tens.put("FORTY", 40);
        tens.put("FIFTY", 50);
        tens.put("SIXTY", 60);
        tens.put("SEVENTY", 70);
        tens.put("EIGHTY", 80);
        tens.put("NINETY", 90);
        int total = 0;
        int current = 0;
        for (final String part : constant.split("_")) {
            if (units.containsKey(part)) {
                current += units.get(part);
            } else if (tens.containsKey(part)) {
                current += tens.get(part);
            } else if ("HUNDRED".equals(part)) {
                current *= 100;
            } else if ("THOUSAND".equals(part)) {
                total += current * 1000;
                current = 0;
            } else if ("MILLION".equals(part)) {
                total += current * 1_000_000;
                current = 0;
            } else if ("BILLION".equals(part)) {
                total += current * 1_000_000_000;
                current = 0;
            } else {
                return null;
            }
        }
        return total + current;
    }
}
