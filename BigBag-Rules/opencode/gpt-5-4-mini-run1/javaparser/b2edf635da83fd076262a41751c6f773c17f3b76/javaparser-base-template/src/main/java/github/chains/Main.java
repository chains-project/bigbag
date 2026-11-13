package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String SNAKEYAML_CONSTRUCTOR_FQN = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String SNAKEYAML_LOADER_OPTIONS_FQN = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try (var paths = Files.walk(sourceRoot)) {
            paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = cu.findAll(ObjectCreationExpr.class).stream()
                    .map(expr -> transformConstructorCall(cu, expr))
                    .reduce(false, Boolean::logicalOr);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Skip files that fail to parse or are not relevant.
        }
    }

    private static boolean transformConstructorCall(CompilationUnit cu, ObjectCreationExpr expr) {
        if (!isSnakeYamlConstructor(cu, expr)) {
            return false;
        }

        if (expr.getArguments().size() != 1 || !(expr.getArgument(0) instanceof ClassExpr)) {
            return false;
        }

        Expression loaderOptions = StaticJavaParser.parseExpression("new " + SNAKEYAML_LOADER_OPTIONS_FQN + "()");
        expr.addArgument(loaderOptions);
        return true;
    }

    private static boolean isSnakeYamlConstructor(CompilationUnit cu, ObjectCreationExpr expr) {
        String typeName = expr.getType().asString();
        if (SNAKEYAML_CONSTRUCTOR_FQN.equals(typeName)) {
            return true;
        }

        if (!"Constructor".equals(expr.getType().getNameAsString())) {
            return false;
        }

        return hasSnakeYamlConstructorImport(cu);
    }

    private static boolean hasSnakeYamlConstructorImport(CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(importDecl -> {
            String imported = importDecl.getNameAsString();
            if (importDecl.isAsterisk()) {
                return "org.yaml.snakeyaml.constructor".equals(imported);
            }
            return SNAKEYAML_CONSTRUCTOR_FQN.equals(imported);
        });
    }
}
