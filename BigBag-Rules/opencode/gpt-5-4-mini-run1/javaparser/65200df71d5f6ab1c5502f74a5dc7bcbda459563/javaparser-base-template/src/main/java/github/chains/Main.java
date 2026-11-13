package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_BUILDER_TYPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";
    private static final String TARGET_METHOD = "addEnabledLanguages";
    private static final Path SONARLINT_CORE_JAR =
            Paths.get(
                    System.getProperty("user.home"),
                    ".m2",
                    "repository",
                    "org",
                    "sonarsource",
                    "sonarlint",
                    "core",
                    "sonarlint-core",
                    "8.19.0.72745",
                    "sonarlint-core-8.19.0.72745.jar");

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        configureParser(sourceRoot);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        }
    }

    private static void configureParser(Path sourceRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver(false));
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        if (Files.exists(SONARLINT_CORE_JAR)) {
            try {
                typeSolver.add(new JarTypeSolver(SONARLINT_CORE_JAR));
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load SonarLint dependency jar", e);
            }
        }
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(configuration);
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            boolean changed = removeDeprecatedEnabledLanguagesCall(compilationUnit);
            if (changed) {
                Files.writeString(file, compilationUnit.toString());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite " + file, e);
        }
    }

    private static boolean removeDeprecatedEnabledLanguagesCall(CompilationUnit compilationUnit) {
        boolean[] changed = new boolean[] {false};
        compilationUnit.findAll(MethodCallExpr.class).forEach(call -> {
            if (!TARGET_METHOD.equals(call.getNameAsString())) {
                return;
            }
            if (!isTargetBuilderScope(call)) {
                return;
            }

            changed[0] = true;
            Optional<Expression> scope = call.getScope();
            if (scope.isEmpty()) {
                call.remove();
                return;
            }

            if (call.getParentNode().filter(parent -> parent instanceof ExpressionStmt).isPresent()) {
                ((ExpressionStmt) call.getParentNode().orElseThrow()).remove();
                return;
            }

            call.replace(scope.get().clone());
        });
        return changed[0];
    }

    private static boolean isTargetBuilderScope(MethodCallExpr call) {
        try {
            return call.getScope()
                    .map(Expression::calculateResolvedType)
                    .map(type -> TARGET_BUILDER_TYPE.equals(type.describe()))
                    .orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
