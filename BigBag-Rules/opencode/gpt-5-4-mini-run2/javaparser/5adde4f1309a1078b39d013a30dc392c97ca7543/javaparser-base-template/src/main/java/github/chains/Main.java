package github.chains;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_METHOD = "addEnabledLanguages";
    private static final String TARGET_SCOPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.builder()";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            System.err.println("Not a directory: " + sourceRoot);
            System.exit(1);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (!isTargetCall(call)) {
                    continue;
                }

                call.replace(call.getScope().orElseThrow().clone());
                changed = true;
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (ParseProblemException e) {
            return;
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean isTargetCall(MethodCallExpr call) {
        return TARGET_METHOD.equals(call.getNameAsString())
                && call.getScope().map(Object::toString).orElse("").equals(TARGET_SCOPE)
                && call.getArguments().size() == 1;
    }
}
