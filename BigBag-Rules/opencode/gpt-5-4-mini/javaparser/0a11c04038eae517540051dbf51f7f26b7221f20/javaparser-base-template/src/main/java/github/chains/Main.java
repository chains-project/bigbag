package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String SNakeyamlConstructor = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String SNakeyamlLoaderOptions = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source root to transform");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);

            boolean changed = false;
            for (ObjectCreationExpr expr : compilationUnit.findAll(ObjectCreationExpr.class)) {
                if (shouldUpgrade(expr)) {
                    expr.addArgument(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean shouldUpgrade(ObjectCreationExpr expr) {
        if (expr.getArguments().size() != 1) {
            return false;
        }

        String typeName = expr.getType().asString();
        return "Constructor".equals(typeName) || SNakeyamlConstructor.equals(typeName);
    }
}
