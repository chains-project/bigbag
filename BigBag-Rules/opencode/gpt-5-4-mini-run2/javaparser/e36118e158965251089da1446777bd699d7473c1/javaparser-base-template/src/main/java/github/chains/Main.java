package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Main {

    private static final String OLD_TYPE = "org.cactoos.map.MapEntry";

    private static final String NEW_IMPORT = "java.util.AbstractMap";

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transform);
        }
    }

    private static void transform(final Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;
            for (final ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                if (isOldMapEntry(expr, cu)) {
                    final String args = expr.getArguments().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                    expr.replace(StaticJavaParser.parseExpression(
                        "new java.util.AbstractMap.SimpleEntry<>(" + args + ")"
                    ));
                    changed = true;
                }
            }
            if (cu.getImports().removeIf(imp -> OLD_TYPE.equals(imp.getNameAsString()))) {
                changed = true;
            }
            if (changed) {
                ensureImport(cu, NEW_IMPORT);
                Files.writeString(path, cu.toString());
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(String.format("Failed to transform %s", path), ex);
        }
    }

    private static boolean isOldMapEntry(final ObjectCreationExpr expr, final CompilationUnit cu) {
        final String simple = expr.getType().getNameAsString();
        final boolean imported = cu.getImports().stream()
            .map(ImportDeclaration::getNameAsString)
            .anyMatch(OLD_TYPE::equals);
        return "MapEntry".equals(simple) && imported;
    }

    private static void ensureImport(final CompilationUnit cu, final String name) {
        if (cu.getImports().stream().noneMatch(imp -> name.equals(imp.getNameAsString()))) {
            cu.addImport(name);
        }
    }
}
