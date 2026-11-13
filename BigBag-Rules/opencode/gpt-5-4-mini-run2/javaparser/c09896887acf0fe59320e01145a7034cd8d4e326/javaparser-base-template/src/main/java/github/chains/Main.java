package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
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
    private static final String OLD_TYPE = "org.kohsuke.github.GHCompare";
    private static final String OLD_MEMBER = "status";
    private static final String NEW_MEMBER = "getStatus";
    private static final String OLD_CALL = "getCompare";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        List<Path> javaFiles;
        try (var stream = Files.walk(sourceRoot)) {
            javaFiles = stream
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .collect(Collectors.toList());
        }

        int changedFiles = 0;
        for (Path file : javaFiles) {
            if (rewriteFile(file)) {
                changedFiles++;
            }
        }

        System.out.println("Updated " + changedFiles + " file(s).");
    }

    private static boolean rewriteFile(Path file) throws IOException {
        String original = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(original);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        for (FieldAccessExpr access : cu.findAll(FieldAccessExpr.class)) {
            if (!OLD_MEMBER.equals(access.getNameAsString())) {
                continue;
            }
            if (!shouldRewrite(access)) {
                continue;
            }

            Expression scope = access.getScope().clone();
            MethodCallExpr replacement = new MethodCallExpr(scope, NEW_MEMBER);
            access.replace(replacement);
            changed = true;
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }

        return changed;
    }

    private static boolean shouldRewrite(FieldAccessExpr access) {
        return containsOldCompareCall(access.getScope());
    }

    private static boolean containsOldCompareCall(Expression expression) {
        if (expression.isMethodCallExpr() && OLD_CALL.equals(expression.asMethodCallExpr().getNameAsString())) {
            return true;
        }
        if (expression.isFieldAccessExpr()) {
            return containsOldCompareCall(expression.asFieldAccessExpr().getScope());
        }
        if (expression.isEnclosedExpr()) {
            return containsOldCompareCall(expression.asEnclosedExpr().getInner());
        }
        if (expression.isCastExpr()) {
            return containsOldCompareCall(expression.asCastExpr().getExpression());
        }
        if (expression.isMethodCallExpr()) {
            MethodCallExpr call = expression.asMethodCallExpr();
            return call.getScope().map(Main::containsOldCompareCall).orElse(false)
                || call.getArguments().stream().anyMatch(Main::containsOldCompareCall);
        }
        return false;
    }
}
