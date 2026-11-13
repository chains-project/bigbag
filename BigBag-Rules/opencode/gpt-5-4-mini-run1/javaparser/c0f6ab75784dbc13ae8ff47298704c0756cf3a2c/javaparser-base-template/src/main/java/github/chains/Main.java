package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source root");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean[] changed = {false};

            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (!"addEnabledLanguages".equals(call.getNameAsString())) {
                    return;
                }

                Expression scope = call.getScope().orElse(null);
                if (scope == null || !isAnalysisEngineConfigurationBuilder(scope)) {
                    return;
                }

                call.replace(scope.clone());
                changed[0] = true;
            });

            if (changed[0]) {
                Files.writeString(file, cu.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean isAnalysisEngineConfigurationBuilder(Expression scope) {
        String normalized = scope.toString().replaceAll("\\s+", "");
        return normalized.endsWith("AnalysisEngineConfiguration.builder()");
    }
}
