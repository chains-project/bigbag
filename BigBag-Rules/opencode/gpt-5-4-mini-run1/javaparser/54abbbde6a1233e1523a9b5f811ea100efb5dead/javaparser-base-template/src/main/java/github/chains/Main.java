package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, String> CONSTANTS = new HashMap<>();

    static {
        CONSTANTS.put("SEVEN", "7");
        CONSTANTS.put("TEN", "10");
        CONSTANTS.put("MILLION", "1000000");
    }

    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Main::isJavaSource)
                .forEach(Main::rewrite);
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean isJavaSource(final Path path) {
        return Files.isRegularFile(path)
            && path.toString().endsWith(".java")
            && !path.toString().contains("/target/");
    }

    private static void rewrite(final Path file) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(file);
            final TvConstantRewriter rewriter = new TvConstantRewriter(unit);
            final CompilationUnit changed = (CompilationUnit) rewriter.visit(unit, null);
            if (rewriter.changed) {
                Files.writeString(file, changed.toString());
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class TvConstantRewriter extends ModifierVisitor<Void> {
        private final CompilationUnit unit;

        private boolean changed;

        private TvConstantRewriter(final CompilationUnit unit) {
            this.unit = unit;
        }

        @Override
        public Node visit(final ImportDeclaration decl, final Void arg) {
            if (this.isOldTvImport(decl)) {
                this.changed = true;
                return null;
            }
            return (Node) super.visit(decl, arg);
        }

        @Override
        public Node visit(final FieldAccessExpr expr, final Void arg) {
            if (this.isTvConstantAccess(expr)) {
                this.changed = true;
                return new IntegerLiteralExpr(CONSTANTS.get(expr.getNameAsString()));
            }
            return (Node) super.visit(expr, arg);
        }

        @Override
        public Node visit(final NameExpr expr, final Void arg) {
            if (this.isStaticImportedTvConstant(expr.getNameAsString())) {
                this.changed = true;
                return new IntegerLiteralExpr(CONSTANTS.get(expr.getNameAsString()));
            }
            return (Node) super.visit(expr, arg);
        }

        private boolean isTvConstantAccess(final FieldAccessExpr expr) {
            final String constant = expr.getNameAsString();
            if (!CONSTANTS.containsKey(constant)) {
                return false;
            }
            return this.matchesOldType(expr.getScope());
        }

        private boolean matchesOldType(final Expression scope) {
            if (scope.isNameExpr()) {
                return OLD_TYPE.equals(scope.asNameExpr().getNameAsString())
                    || "Tv".equals(scope.asNameExpr().getNameAsString());
            }
            if (scope.isFieldAccessExpr()) {
                final FieldAccessExpr field = scope.asFieldAccessExpr();
                final String text = field.toString();
                return OLD_TYPE.equals(text) || text.endsWith(".Tv");
            }
            return false;
        }

        private boolean isStaticImportedTvConstant(final String name) {
            if (!CONSTANTS.containsKey(name)) {
                return false;
            }
            return this.unit.getImports().stream().anyMatch(importDecl ->
                importDecl.isStatic()
                    && (importDecl.getNameAsString().equals(OLD_TYPE)
                    || importDecl.getNameAsString().equals(OLD_TYPE + "." + name))
            );
        }

        private boolean isOldTvImport(final ImportDeclaration decl) {
            return decl.getNameAsString().equals(OLD_TYPE)
                || (decl.isStatic() && decl.getNameAsString().startsWith(OLD_TYPE + "."));
        }
    }
}
