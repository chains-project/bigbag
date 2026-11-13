package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.io.UncheckedIOException;

public class Main {
    private static final String BROKEN_METHOD = "enableLogging";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(Files.readString(path, StandardCharsets.UTF_8));
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (!isBrokenEnableLoggingCall(call)) {
                    continue;
                }
                call.findAncestor(ExpressionStmt.class).ifPresent(ExpressionStmt::remove);
                changed = true;
            }

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to rewrite " + path, ex);
        }
    }

    private static boolean isBrokenEnableLoggingCall(MethodCallExpr call) {
        return BROKEN_METHOD.equals(call.getNameAsString()) && call.getArguments().size() == 1;
    }
}
