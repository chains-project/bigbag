package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String TV_FQCN = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();

    static {
        TV_CONSTANTS.put("THREE", 3);
        TV_CONSTANTS.put("FOUR", 4);
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("SIX", 6);
        TV_CONSTANTS.put("SEVEN", 7);
        TV_CONSTANTS.put("EIGHT", 8);
        TV_CONSTANTS.put("NINE", 9);
        TV_CONSTANTS.put("TEN", 10);
        TV_CONSTANTS.put("FIFTEEN", 15);
        TV_CONSTANTS.put("TWENTY", 20);
        TV_CONSTANTS.put("THIRTY", 30);
        TV_CONSTANTS.put("FORTY", 40);
        TV_CONSTANTS.put("FIFTY", 50);
        TV_CONSTANTS.put("SIXTY", 60);
        TV_CONSTANTS.put("SEVENTY", 70);
        TV_CONSTANTS.put("EIGHTY", 80);
        TV_CONSTANTS.put("NINETY", 90);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
        TV_CONSTANTS.put("MILLION", 1000000);
        TV_CONSTANTS.put("BILLION", 1000000000);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(String.format("Not a directory: %s", root));
        }
        try (Stream<Path> files = Files.walk(root)) {
            final List<Path> javaFiles = files
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            for (final Path file : javaFiles) {
                rewrite(file);
            }
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to walk %s", root), err);
        }
    }

    private static void rewrite(final Path file) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);
        final boolean[] changed = {false};
        new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(final FieldAccessExpr expr, final Void arg) {
                final Integer value = tvValue(expr.getNameAsString(), expr.getScope().toString());
                if (value != null) {
                    changed[0] = true;
                    return new IntegerLiteralExpr(String.valueOf(value));
                }
                return super.visit(expr, arg);
            }

            @Override
            public Visitable visit(final NameExpr expr, final Void arg) {
                final Integer value = TV_CONSTANTS.get(expr.getNameAsString());
                if (value != null && hasStaticTvImport(cu)) {
                    changed[0] = true;
                    return new IntegerLiteralExpr(String.valueOf(value));
                }
                return super.visit(expr, arg);
            }
        }.visit(cu, null);
        if (changed[0]) {
            removeTvImports(cu);
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static Integer tvValue(final String name, final String scope) {
        final Integer value = TV_CONSTANTS.get(name);
        if (value == null) {
            return null;
        }
        if (scope.equals("Tv") || scope.endsWith("." + TV_FQCN) || scope.endsWith(".Tv")) {
            return value;
        }
        return null;
    }

    private static boolean hasStaticTvImport(final CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(importDecl -> importDecl.isStatic()
            && importDecl.getNameAsString().equals(TV_FQCN));
    }

    private static void removeTvImports(final CompilationUnit cu) {
        cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(TV_FQCN));
    }
}
