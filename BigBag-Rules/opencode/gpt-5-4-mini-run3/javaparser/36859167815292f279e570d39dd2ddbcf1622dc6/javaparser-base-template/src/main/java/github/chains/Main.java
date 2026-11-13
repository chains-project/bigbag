package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "FopFactory";
    private static final String OLD_METHOD = "newInstance";
    private static final String NEW_EXPRESSION =
            "new org.apache.fop.apps.FopFactoryBuilder(java.nio.file.Paths.get(\"\").toAbsolutePath().toUri()).build()";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root directory");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                transform(javaFile);
            }
        }
    }

    private static void transform(Path javaFile) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (isOldFopFactoryCall(call)) {
                call.replace(StaticJavaParser.parseExpression(NEW_EXPRESSION));
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(javaFile, LexicalPreservingPrinter.print(cu));
        }
    }

    private static boolean isOldFopFactoryCall(MethodCallExpr call) {
        if (!OLD_METHOD.equals(call.getNameAsString()) || !call.getArguments().isEmpty()) {
            return false;
        }

        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return false;
        }

        String scopeText = scope.get().toString();
        return scopeText.endsWith(OLD_TYPE) || scopeText.equals(OLD_TYPE) || scopeText.endsWith("." + OLD_TYPE);
    }
}
