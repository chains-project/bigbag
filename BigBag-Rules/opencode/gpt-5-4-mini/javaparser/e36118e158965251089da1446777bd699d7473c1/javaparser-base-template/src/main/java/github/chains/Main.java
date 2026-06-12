package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public final class Main {

    private static final String OLD_TYPE = "org.cactoos.map.MapEntry";

    private static final String NEW_TYPE = "java.util.AbstractMap.SimpleEntry";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]);
        final JavaParser parser = new JavaParser(new ParserConfiguration());
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> transform(parser, path));
        }
    }

    private static void transform(final JavaParser parser, final Path path) {
        try {
            final CompilationUnit unit = parser.parse(path).getResult().orElseThrow(
                () -> new IllegalStateException("Failed to parse " + path)
            );
            boolean changed = false;
            changed |= removeOldImport(unit);
            changed |= addNewImport(unit);
            changed |= replaceConstructorCalls(unit);
            if (changed) {
                Files.write(path, unit.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to transform " + path, ex);
        }
    }

    private static boolean removeOldImport(final CompilationUnit unit) {
        final boolean[] changed = {false};
        unit.getImports().removeIf(imp -> {
            final boolean match = !imp.isAsterisk() && !imp.isStatic()
                && OLD_TYPE.equals(imp.getNameAsString());
            changed[0] = changed[0] || match;
            return match;
        });
        return changed[0];
    }

    private static boolean addNewImport(final CompilationUnit unit) {
        if (unit.getImports().stream().anyMatch(imp -> !imp.isStatic() && NEW_TYPE.equals(imp.getNameAsString()))) {
            return false;
        }
        if (unit.findAll(ObjectCreationExpr.class).stream().noneMatch(Main::isMapEntryLike)) {
            return false;
        }
        unit.addImport(NEW_TYPE);
        return true;
    }

    private static boolean replaceConstructorCalls(final CompilationUnit unit) {
        final boolean[] changed = {false};
        unit.findAll(ObjectCreationExpr.class).forEach(expr -> {
            if (isMapEntryLike(expr)) {
                final String args = expr.getArguments().toString().replace('[', ' ').replace(']', ' ').trim();
                expr.replace(StaticJavaParser.parseExpression(
                    String.format("new java.util.AbstractMap.SimpleEntry<String, String>(%s)", args)
                ));
                changed[0] = true;
            }
        });
        return changed[0];
    }

    private static boolean isMapEntryLike(final ObjectCreationExpr expr) {
        final String type = expr.getType().getNameWithScope();
        return "MapEntry".equals(type) || OLD_TYPE.equals(type)
            || OLD_TYPE.equals(expr.getType().asString()) || "SimpleEntry".equals(type)
            || NEW_TYPE.equals(expr.getType().asString());
    }
}
