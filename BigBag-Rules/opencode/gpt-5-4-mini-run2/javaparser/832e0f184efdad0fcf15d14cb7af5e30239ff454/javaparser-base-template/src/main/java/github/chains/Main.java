package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final String OLD_METHOD = "parseEnchantment";
    private static final String NEW_METHOD = "getEnchant";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        JavaParser parser = new JavaParser();
        AtomicInteger changedFiles = new AtomicInteger();

        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, path, changedFiles));
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source root: " + sourceRoot, e);
        }

        System.out.println("Updated " + changedFiles.get() + " files");
    }

    private static void transformFile(JavaParser parser, Path path, AtomicInteger changedFiles) {
        try {
            ParseResult<CompilationUnit> parseResult = parser.parse(path);
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                return;
            }

            CompilationUnit cu = parseResult.getResult().get();
            LexicalPreservingPrinter.setup(cu);

            MethodRenamer renamer = new MethodRenamer();
            cu.accept(renamer, null);
            boolean changed = renamer.changed;
            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
                changedFiles.incrementAndGet();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static final class MethodRenamer extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (OLD_METHOD.equals(n.getNameAsString())) {
                n.setName(NEW_METHOD);
                changed = true;
                return n;
            }
            return n;
        }
    }
}
