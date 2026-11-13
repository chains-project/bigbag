package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            CompilationUnit cu = parser.parse(path).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            LexicalPreservingPrinter.setup(cu);
            ConnectorUpgradeVisitor visitor = new ConnectorUpgradeVisitor();
            visitor.visit(cu, null);

            if (!visitor.changed) {
                return;
            }

            cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
            if (cu.getImports().stream().noneMatch(importDecl -> importDecl.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"))) {
                cu.addImport("org.eclipse.jetty.server.ServerConnector");
            }

            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static final class ConnectorUpgradeVisitor extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public ObjectCreationExpr visit(ObjectCreationExpr expr, Void arg) {
            ObjectCreationExpr visited = (ObjectCreationExpr) super.visit(expr, arg);
            if (!isSelectChannelConnector(visited)) {
                return visited;
            }

            Optional<Expression> serverExpression = inferServerExpression(visited);
            if (!serverExpression.isPresent()) {
                return visited;
            }

            changed = true;
            return new ObjectCreationExpr(
                    null,
                    new ClassOrInterfaceType(null, "ServerConnector"),
                    com.github.javaparser.ast.NodeList.nodeList(serverExpression.get().clone())
            );
        }

        private boolean isSelectChannelConnector(ObjectCreationExpr expr) {
            return expr.getType().getNameAsString().equals("SelectChannelConnector")
                    || expr.getType().asString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
        }

        private Optional<Expression> inferServerExpression(ObjectCreationExpr expr) {
            Optional<MethodCallExpr> directAddConnector = expr.findAncestor(MethodCallExpr.class)
                    .filter(call -> call.getNameAsString().equals("addConnector") && call.getArguments().contains(expr));
            if (directAddConnector.isPresent()) {
                return directAddConnector.get().getScope();
            }

            Optional<String> assignedName = inferAssignedName(expr);
            if (!assignedName.isPresent()) {
                return Optional.empty();
            }

            for (MethodCallExpr call : expr.findRootNode().findAll(MethodCallExpr.class)) {
                if (!call.getNameAsString().equals("addConnector") || !call.getScope().isPresent()) {
                    continue;
                }
                if (referencesName(call.getArguments(), assignedName.get())) {
                    return call.getScope();
                }
            }

            return Optional.empty();
        }

        private Optional<String> inferAssignedName(ObjectCreationExpr expr) {
            Optional<Node> parent = expr.getParentNode();
            if (!parent.isPresent()) {
                return Optional.empty();
            }

            Node node = parent.get();
            if (node instanceof VariableDeclarator) {
                return Optional.of(((VariableDeclarator) node).getNameAsString());
            }
            if (node instanceof com.github.javaparser.ast.expr.AssignExpr) {
                com.github.javaparser.ast.expr.AssignExpr assignExpr = (com.github.javaparser.ast.expr.AssignExpr) node;
                if (assignExpr.getTarget() instanceof NameExpr) {
                    return Optional.of(((NameExpr) assignExpr.getTarget()).getNameAsString());
                }
                if (assignExpr.getTarget() instanceof FieldAccessExpr) {
                    return Optional.of(((FieldAccessExpr) assignExpr.getTarget()).getNameAsString());
                }
            }
            return Optional.empty();
        }

        private boolean referencesName(com.github.javaparser.ast.NodeList<Expression> expressions, String name) {
            for (Expression expression : expressions) {
                if (referencesName(expression, name)) {
                    return true;
                }
            }
            return false;
        }

        private boolean referencesName(Expression expression, String name) {
            if (expression instanceof NameExpr) {
                return ((NameExpr) expression).getNameAsString().equals(name);
            }
            if (expression instanceof FieldAccessExpr) {
                FieldAccessExpr fieldAccessExpr = (FieldAccessExpr) expression;
                return fieldAccessExpr.getNameAsString().equals(name)
                        || referencesName(fieldAccessExpr.getScope(), name);
            }
            for (Node child : expression.getChildNodes()) {
                if (child instanceof Expression && referencesName((Expression) child, name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
