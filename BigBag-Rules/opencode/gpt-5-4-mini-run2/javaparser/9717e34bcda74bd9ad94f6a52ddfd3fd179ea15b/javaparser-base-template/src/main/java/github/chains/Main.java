package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final String REMOVED_TYPE = "Tv";

    private static final Map<String, Integer> CONSTANTS = new HashMap<>();

    static {
        CONSTANTS.put("THREE", 3);
        CONSTANTS.put("FOUR", 4);
        CONSTANTS.put("FIVE", 5);
        CONSTANTS.put("SIX", 6);
        CONSTANTS.put("TEN", 10);
        CONSTANTS.put("FIFTEEN", 15);
        CONSTANTS.put("TWENTY", 20);
        CONSTANTS.put("THIRTY", 30);
        CONSTANTS.put("HUNDRED", 100);
        CONSTANTS.put("THOUSAND", 1000);
        CONSTANTS.put("BILLION", 1000000000);
    }

    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        final ParserConfiguration configuration = new ParserConfiguration();
        final JavaParser parser = new JavaParser(configuration);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> transform(parser, path));
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to walk source tree: " + root, ex);
        }
    }

    private static void transform(final JavaParser parser, final Path path) {
        try {
            final CompilationUnit cu = parser.parse(path, StandardCharsets.UTF_8).getResult()
                .orElseThrow(() -> new IllegalStateException("Cannot parse " + path));
            LexicalPreservingPrinter.setup(cu);
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(final FieldAccessExpr expr, final Void arg) {
                    final Integer replacement = constantValue(expr);
                    if (replacement != null) {
                        return new IntegerLiteralExpr(Integer.toString(replacement));
                    }
                    return super.visit(expr, arg);
                }

                private Integer constantValue(final FieldAccessExpr expr) {
                    if (!CONSTANTS.containsKey(expr.getNameAsString())) {
                        return null;
                    }
                    final Expression scope = expr.getScope();
                    if (scope.isNameExpr() && REMOVED_TYPE.equals(scope.asNameExpr().getNameAsString())) {
                        return CONSTANTS.get(expr.getNameAsString());
                    }
                    if (scope.isFieldAccessExpr() && endsWithRemovedType(scope.asFieldAccessExpr())) {
                        return CONSTANTS.get(expr.getNameAsString());
                    }
                    return null;
                }

                private boolean endsWithRemovedType(final FieldAccessExpr expr) {
                    if (expr.getNameAsString().equals(REMOVED_TYPE)) {
                        return true;
                    }
                    final Expression scope = expr.getScope();
                    return scope.isFieldAccessExpr() && endsWithRemovedType(scope.asFieldAccessExpr());
                }
            }, null);
            cu.getImports().removeIf(imp -> !imp.isStatic() && imp.getNameAsString().equals("com.jcabi.aspects.Tv"));
            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to transform " + path, ex);
        }
    }
}
