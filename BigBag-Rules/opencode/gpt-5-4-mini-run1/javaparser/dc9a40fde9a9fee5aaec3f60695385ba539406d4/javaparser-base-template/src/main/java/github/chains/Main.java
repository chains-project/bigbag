package github.chains;

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
    private static final String OLD_METHOD = "getTargetServiceUniqueName";
    private static final String NEW_METHOD = "getServiceName";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean[] changed = {false};
            compilationUnit.findAll(MethodCallExpr.class).forEach(call -> {
                if (OLD_METHOD.equals(call.getNameAsString()) && call.getArguments().isEmpty()) {
                    call.setName(NEW_METHOD);
                    changed[0] = true;
                }
            });

            if (changed[0]) {
                Files.write(file, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }
}
