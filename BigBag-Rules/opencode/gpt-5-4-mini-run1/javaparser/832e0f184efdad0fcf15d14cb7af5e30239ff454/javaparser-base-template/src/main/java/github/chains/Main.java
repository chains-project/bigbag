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
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try {
            Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean usesXEnchantment = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("com.cryptomorin.xseries.XEnchantment"));

            AtomicBoolean changed = new AtomicBoolean(false);

            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (!"parseEnchantment".equals(call.getNameAsString()) || !call.getArguments().isEmpty()) {
                    return;
                }

                boolean matchesStructuralXEnchantmentUse = usesXEnchantment
                    || call.getScope().map(scope -> scope.toString().contains("XEnchantment")).orElse(false);

                if (matchesStructuralXEnchantmentUse) {
                    call.setName("getEnchant");
                    changed.set(true);
                }
            });

            if (changed.get()) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }
}
