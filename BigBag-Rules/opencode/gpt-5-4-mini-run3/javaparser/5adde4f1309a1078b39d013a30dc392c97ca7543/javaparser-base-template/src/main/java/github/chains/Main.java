package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String ANALYSIS_ENGINE_CONFIGURATION =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + root, e);
        }
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            CompilationUnit cu = parser.parse(source).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            LexicalPreservingPrinter.setup(cu);
            final boolean[] changed = {false};

            cu.findAll(MethodCallExpr.class).stream()
                    .filter(Main::isRemovedBuilderCall)
                    .forEach(call -> {
                        Expression scope = call.getScope().orElseThrow(() -> new IllegalStateException("Missing scope"));
                        call.replace(scope);
                        changed[0] = true;
                    });

            if (changed[0]) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        } catch (ParseProblemException e) {
            throw new RuntimeException("Failed to parse " + path, e);
        }
    }

    private static boolean isRemovedBuilderCall(MethodCallExpr call) {
        if (!"addEnabledLanguages".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
            return false;
        }
        return call.getScope()
                .filter(Expression::isMethodCallExpr)
                .map(Expression::asMethodCallExpr)
                .filter(builder -> "builder".equals(builder.getNameAsString()))
                .filter(Main::isAnalysisEngineConfigurationBuilder)
                .isPresent();
    }

    private static boolean isAnalysisEngineConfigurationBuilder(MethodCallExpr builderCall) {
        String scopeText = builderCall.getScope().map(Expression::toString).orElse("");
        if (scopeText.endsWith("AnalysisEngineConfiguration")) {
            return true;
        }
        return builderCall.findCompilationUnit()
                .map(cu -> cu.getImports().stream()
                        .anyMatch(i -> !i.isStatic() && !i.isAsterisk()
                                && ANALYSIS_ENGINE_CONFIGURATION.equals(i.getNameAsString())))
                .orElse(false);
    }
}
