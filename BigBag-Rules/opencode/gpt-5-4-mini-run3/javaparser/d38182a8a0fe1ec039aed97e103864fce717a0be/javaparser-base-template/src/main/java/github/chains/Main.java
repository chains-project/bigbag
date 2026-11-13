package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "Authentication.User";

    private static final String NEW_IMPORT = "com.artipie.http.auth.AuthUser";

    private static final String NEW_TYPE = "AuthUser";

    public static void main(final String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root path");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transform);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to walk " + root, err);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(unit);
            final AuthUserVisitor visitor = new AuthUserVisitor();
            visitor.visit(unit, null);
            if (visitor.changed) {
                unit.addImport(NEW_IMPORT);
                unit.getImports().removeIf(Main::isOldImport);
                Files.writeString(file, LexicalPreservingPrinter.print(unit));
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to process " + file, err);
        }
    }

    private static boolean isOldImport(final ImportDeclaration imp) {
        return imp.getNameAsString().equals("com.artipie.http.auth.Authentication.User");
    }

    private static final class AuthUserVisitor extends ModifierVisitor<Void> {

        private boolean changed;

        @Override
        public ClassOrInterfaceType visit(final ClassOrInterfaceType type, final Void arg) {
            super.visit(type, arg);
            if (isOldType(type)) {
                this.changed = true;
                return new ClassOrInterfaceType(null, NEW_TYPE);
            }
            return type;
        }

        @Override
        public ObjectCreationExpr visit(final ObjectCreationExpr expr, final Void arg) {
            final boolean old = isOldType(expr.getType());
            final ObjectCreationExpr updated = (ObjectCreationExpr) super.visit(expr, arg);
            if (old && updated.getArguments().size() == 1) {
                this.changed = true;
                updated.getArguments().add(new StringLiteralExpr(""));
            }
            return updated;
        }

        private boolean isOldType(final ClassOrInterfaceType type) {
            final String name = qualifiedName(type);
            return name.equals(OLD_TYPE) || name.endsWith("." + OLD_TYPE);
        }

        private String qualifiedName(final ClassOrInterfaceType type) {
            return type.getScope().map(this::qualifiedName).map(prefix -> prefix + ".").orElse("")
                + type.getNameAsString();
        }
    }
}
