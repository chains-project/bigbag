package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Main {

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root");
        }
        final Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }
        final JavaParser parser = new JavaParser(new ParserConfiguration());
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    transform(parser, path);
                } catch (final IOException err) {
                    throw new IllegalStateException("Failed to transform " + path, err);
                }
            });
        }
    }

    private static void transform(final JavaParser parser, final Path file) throws IOException {
        final String source = Files.readString(file, StandardCharsets.UTF_8);
        final CompilationUnit cu = parser.parse(source).getResult()
            .orElseThrow(() -> new IllegalStateException("Cannot parse " + file));
        LexicalPreservingPrinter.setup(cu);
        final Map<String, String> types = new HashMap<>(64);
        cu.accept(new VoidVisitorAdapter<Map<String, String>>() {
            @Override
            public void visit(final FieldDeclaration node, final Map<String, String> map) {
                node.getVariables().forEach(
                    var -> map.put(var.getNameAsString(), var.getType().asString())
                );
                super.visit(node, map);
            }

            @Override
            public void visit(final Parameter node, final Map<String, String> map) {
                map.put(node.getNameAsString(), node.getType().asString());
                super.visit(node, map);
            }

            @Override
            public void visit(final VariableDeclarator node, final Map<String, String> map) {
                map.put(node.getNameAsString(), node.getType().asString());
                super.visit(node, map);
            }
        }, types);
        rewriteImports(cu);
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> rewriteAuthSlice(expr, types));
        Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
    }

    private static void rewriteImports(final CompilationUnit cu) {
        cu.findAll(ImportDeclaration.class).forEach(imp -> {
            final String name = imp.getNameAsString();
            if ("com.artipie.http.auth.AuthSlice".equals(name)) {
                imp.setName("com.artipie.http.auth.BasicAuthSlice");
            }
        });
    }

    private static void rewriteAuthSlice(
        final ObjectCreationExpr expr,
        final Map<String, String> types
    ) {
        if (!"AuthSlice".equals(expr.getType().getNameAsString())) {
            return;
        }
        final List<Expression> args = expr.getArguments();
        if (args.size() != 3) {
            return;
        }
        final Expression auth = unwrap(args.get(1));
        final Expression perm = unwrap(args.get(2));
        if (isAuthenticationLike(auth, types) && isPermissionLike(perm, types)) {
            expr.setType(new ClassOrInterfaceType(null, "BasicAuthSlice"));
            expr.setArgument(1, args.get(2).clone());
            expr.setArgument(2, args.get(1).clone());
        }
    }

    private static Expression unwrap(final Expression expr) {
        if (expr instanceof EnclosedExpr) {
            return unwrap(((EnclosedExpr) expr).getInner());
        }
        return expr;
    }

    private static boolean isAuthenticationLike(
        final Expression expr,
        final Map<String, String> types
    ) {
        final String type = typeOf(expr, types);
        return type != null && (type.endsWith("Authentication") || type.contains(".Authentication"));
    }

    private static boolean isPermissionLike(final Expression expr, final Map<String, String> types) {
        final String type = typeOf(expr, types);
        return type != null && (type.endsWith("Permission") || type.contains(".Permission")
            || expr instanceof LambdaExpr || expr instanceof MethodReferenceExpr);
    }

    private static String typeOf(final Expression expr, final Map<String, String> types) {
        final Expression unwrapped = unwrap(expr);
        if (unwrapped instanceof NameExpr) {
            return types.get(((NameExpr) unwrapped).getNameAsString());
        }
        if (unwrapped instanceof FieldAccessExpr) {
            return types.get(((FieldAccessExpr) unwrapped).getNameAsString());
        }
        if (unwrapped instanceof ObjectCreationExpr) {
            return ((ObjectCreationExpr) unwrapped).getType().asString();
        }
        return null;
    }

}
