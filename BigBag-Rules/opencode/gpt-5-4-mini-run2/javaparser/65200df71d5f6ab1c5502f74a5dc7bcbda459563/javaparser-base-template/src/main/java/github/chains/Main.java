package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    private static final String TARGET_METHOD = "addEnabledLanguages";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);

        List<Path> javaFiles;
        try (var stream = Files.walk(sourceRoot)) {
            javaFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        for (Path file : javaFiles) {
            transformFile(file);
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit compilationUnit;
        try {
            compilationUnit = StaticJavaParser.parse(file);
        } catch (Exception e) {
            return;
        }

        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;
        for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!TARGET_METHOD.equals(call.getNameAsString()) || call.getScope().isEmpty()) {
                continue;
            }

            Expression receiver = call.getScope().get();
            call.replace(receiver);
            changed = true;
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }
}
