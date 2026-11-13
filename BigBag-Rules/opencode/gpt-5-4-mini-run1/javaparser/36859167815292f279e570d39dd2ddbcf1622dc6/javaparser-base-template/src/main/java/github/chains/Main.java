package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_TYPE = "org.apache.fop.apps.FopFactory";
    private static final String TARGET_METHOD = "newInstance";
    private static final String REPLACEMENT_ARGUMENT = "new java.io.File(\".\").toURI()";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path javaFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
                if (isOldFopFactoryCall(call)) {
                    call.addArgument(StaticJavaParser.parseExpression(REPLACEMENT_ARGUMENT));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit));
            }
        } catch (Exception ignored) {
            // Skip files that do not parse cleanly in the target project.
        }
    }

    private static boolean isOldFopFactoryCall(MethodCallExpr call) {
        if (!TARGET_METHOD.equals(call.getNameAsString()) || !call.getArguments().isEmpty()) {
            return false;
        }
        if (!call.getScope().isPresent()) {
            return false;
        }

        String scopeText = call.getScope().get().toString();
        return TARGET_TYPE.equals(scopeText) || "FopFactory".equals(scopeText);
    }
}
