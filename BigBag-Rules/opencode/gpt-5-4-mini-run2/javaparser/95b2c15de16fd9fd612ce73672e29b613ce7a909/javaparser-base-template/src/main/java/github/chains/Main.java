package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_SERVLET = "javax.servlet";
    private static final String NEW_SERVLET = "jakarta.servlet";
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(Main::transformSafely);
        }
    }

    private static void transformSafely(Path path) {
        try {
            transform(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static void transform(Path path) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        changed |= rewriteImports(cu);
        changed |= rewriteSimpleTypes(cu);
        changed |= rewriteConnectorCreations(cu);

        if (changed) {
            Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        for (int i = 0; i < cu.getImports().size(); i++) {
            String name = cu.getImports().get(i).getNameAsString();
            if (name.startsWith(OLD_SERVLET)) {
                cu.getImports().get(i).setName(name.replaceFirst(OLD_SERVLET, NEW_SERVLET));
                changed = true;
            }
            if (OLD_CONNECTOR.equals(name)) {
                cu.getImports().get(i).setName(NEW_CONNECTOR);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteSimpleTypes(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String name = type.getNameAsString();
            if ("SelectChannelConnector".equals(name)) {
                type.setName("ServerConnector");
                changed = true;
            }
            if (name.startsWith("HttpServlet") || name.equals("ServletRequest") || name.equals("ServletResponse") || name.equals("ServletException")) {
                // These are already handled through imports; keep the AST stable.
            }
        }
        return changed;
    }

    private static boolean rewriteConnectorCreations(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            String typeName = creation.getType().getNameAsString();
            if (!"SelectChannelConnector".equals(typeName) && !("ServerConnector".equals(typeName) && creation.getArguments().isEmpty())) {
                continue;
            }

            creation.setType("ServerConnector");
            creation.getArguments().clear();
            creation.addArgument(inferServerExpression(creation).orElse(new NameExpr("server")));
            changed = true;
        }
        return changed;
    }

    private static Optional<Expression> inferServerExpression(ObjectCreationExpr creation) {
        Optional<String> variableName = inferAssignedName(creation);
        if (!variableName.isPresent()) {
            return Optional.empty();
        }

        Node root = creation;
        while (root.getParentNode().isPresent()) {
            root = root.getParentNode().get();
        }

        for (MethodCallExpr call : root.findAll(MethodCallExpr.class)) {
            if (!"addConnector".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }
            if (!matchesName(call.getArgument(0), variableName.get())) {
                continue;
            }
            if (call.getScope().isPresent()) {
                return Optional.of(call.getScope().get().clone());
            }
        }

        return inferServerField(root);
    }

    private static Optional<String> inferAssignedName(ObjectCreationExpr creation) {
        if (!creation.getParentNode().isPresent()) {
            return Optional.empty();
        }

        Node parent = creation.getParentNode().get();
        if (parent instanceof com.github.javaparser.ast.body.VariableDeclarator) {
            return Optional.of(((com.github.javaparser.ast.body.VariableDeclarator) parent).getNameAsString());
        }

        if (parent instanceof com.github.javaparser.ast.expr.AssignExpr) {
            Expression target = ((com.github.javaparser.ast.expr.AssignExpr) parent).getTarget();
            if (target.isNameExpr()) {
                return Optional.of(target.asNameExpr().getNameAsString());
            }
            if (target.isFieldAccessExpr()) {
                return Optional.of(target.asFieldAccessExpr().getNameAsString());
            }
        }

        return Optional.empty();
    }

    private static boolean matchesName(Expression expression, String name) {
        if (expression.isNameExpr()) {
            return name.equals(expression.asNameExpr().getNameAsString());
        }
        if (expression.isFieldAccessExpr()) {
            return name.equals(expression.asFieldAccessExpr().getNameAsString());
        }
        return false;
    }

    private static Optional<Expression> inferServerField(Node root) {
        for (com.github.javaparser.ast.body.FieldDeclaration field : root.findAll(com.github.javaparser.ast.body.FieldDeclaration.class)) {
            for (com.github.javaparser.ast.body.VariableDeclarator variable : field.getVariables()) {
                if ("server".equals(variable.getNameAsString()) && variable.getType().asString().endsWith("Server")) {
                    return Optional.of(new FieldAccessExpr(new ThisExpr(), variable.getNameAsString()));
                }
            }
        }
        return Optional.empty();
    }
}
