package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_METHOD = "addEnabledLanguages";

    public static void main(String[] args) {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        StaticJavaParser.setConfiguration(configuration);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Main::isJavaFile).sorted(Comparator.naturalOrder()).forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan source root: " + sourceRoot, e);
        }
    }

    private static boolean isJavaFile(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            boolean changed = false;

            for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
                if (isTargetCall(call) && call.getScope().isPresent()) {
                    Expression scope = call.getScope().orElseThrow().clone();
                    call.replace(scope);
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean isTargetCall(MethodCallExpr call) {
        return TARGET_METHOD.equals(call.getNameAsString());
    }
}
