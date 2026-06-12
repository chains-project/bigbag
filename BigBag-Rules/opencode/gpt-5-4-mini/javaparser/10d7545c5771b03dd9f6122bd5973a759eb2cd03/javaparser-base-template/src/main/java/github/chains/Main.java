package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String BUILDER_TYPE = "JerseyClientBuilder";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }

        Path sourceRoot = Paths.get(args[0]);
        JavaParser parser = new JavaParser();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> rewriteFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree", e);
        }
    }

    private static void rewriteFile(JavaParser parser, Path path) {
        try {
            CompilationUnit cu = parser.parse(path).getResult()
                    .orElseThrow(() -> new IllegalStateException("Unable to parse " + path));

            boolean changed = rewriteCompilationUnit(cu);
            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean rewriteCompilationUnit(CompilationUnit cu) {
        boolean changed = false;

        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            if (isOldJerseyClientBuilder(creationExpr) && creationExpr.getArguments().size() == 1) {
                Expression argument = creationExpr.getArgument(0);
                creationExpr.setArgument(0, wrapMetricsCall(argument));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean isOldJerseyClientBuilder(ObjectCreationExpr expr) {
        return expr.getType().asString().equals(BUILDER_TYPE);
    }

    private static MethodCallExpr wrapMetricsCall(Expression argument) {
        if (argument instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) argument;
            if (call.getNameAsString().equals("metrics") || call.getNameAsString().equals("getMetrics")) {
                return call;
            }
        }
        return StaticJavaParser.parseExpression(argument + ".metrics()").asMethodCallExpr();
    }
}
