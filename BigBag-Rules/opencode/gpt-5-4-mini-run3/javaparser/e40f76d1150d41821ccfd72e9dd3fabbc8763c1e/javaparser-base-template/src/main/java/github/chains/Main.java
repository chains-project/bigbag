package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (!"getJavaScriptResult".equals(call.getNameAsString()) || call.getScope().isEmpty()) {
                    continue;
                }

                Expression scope = call.getScope().get();
                if (scope.isObjectCreationExpr() && isScriptResult(scope.asObjectCreationExpr())) {
                    call.replace(scope.asObjectCreationExpr().getArgument(0).clone());
                    changed = true;
                }
            }

            if (changed) {
                cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().endsWith("ScriptResult"));
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isScriptResult(ObjectCreationExpr creationExpr) {
        return creationExpr.getType().getNameAsString().equals("ScriptResult") && creationExpr.getArguments().size() == 1;
    }
}
