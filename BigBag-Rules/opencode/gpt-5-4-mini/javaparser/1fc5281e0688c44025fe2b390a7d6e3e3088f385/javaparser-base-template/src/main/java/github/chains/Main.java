package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
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
    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            String original = Files.readString(path, StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(original);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if ("SelectChannelConnector".equals(type.getNameAsString())) {
                    type.setName("ServerConnector");
                    changed = true;
                }
            }

            for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
                if (!"SelectChannelConnector".equals(creation.getType().getNameAsString())) {
                    continue;
                }

                Optional<Expression> serverExpr = inferServerExpression(creation);
                if (serverExpr.isPresent()) {
                    creation.setType("ServerConnector");
                    creation.setArguments(NodeList.nodeList(serverExpr.get()));
                    changed = true;
                }
            }

            if (changed) {
                updateImports(cu);
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static Optional<Expression> inferServerExpression(ObjectCreationExpr creation) {
        Optional<String> connectorName = creation.findAncestor(VariableDeclarator.class)
                .map(VariableDeclarator::getNameAsString);

        Optional<MethodCallExpr> enclosingAddConnector = creation.findAncestor(MethodCallExpr.class)
                .filter(call -> "addConnector".equals(call.getNameAsString()));

        if (enclosingAddConnector.isPresent()) {
            MethodCallExpr call = enclosingAddConnector.get();
            if (call.getArguments().stream().anyMatch(arg -> sameConnectorReference(arg, connectorName))) {
                return Optional.of(call.getScope().orElseGet(() -> new NameExpr("server")));
            }
        }

        if (connectorName.isPresent()) {
            return creation.findCompilationUnit().stream()
                    .flatMap(cu -> cu.findAll(MethodCallExpr.class).stream())
                    .filter(call -> "addConnector".equals(call.getNameAsString()))
                    .filter(call -> call.getArguments().stream().anyMatch(arg -> sameConnectorReference(arg, connectorName)))
                    .map(call -> call.getScope().orElseGet(() -> new NameExpr("server")))
                    .findFirst();
        }

        return Optional.empty();
    }

    private static boolean sameConnectorReference(Expression expr, Optional<String> connectorName) {
        if (connectorName.isEmpty()) {
            return false;
        }
        String name = connectorName.get();
        if (expr.isNameExpr()) {
            return name.equals(expr.asNameExpr().getNameAsString());
        }
        if (expr.isFieldAccessExpr()) {
            FieldAccessExpr fa = expr.asFieldAccessExpr();
            return name.equals(fa.getNameAsString())
                    && (fa.getScope().isThisExpr() || "this".equals(fa.getScope().toString()));
        }
        return name.equals(expr.toString()) || ("this." + name).equals(expr.toString());
    }

    private static void updateImports(CompilationUnit cu) {
        cu.getImports().stream()
                .filter(imp -> !imp.isAsterisk())
                .filter(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"))
                .findFirst()
                .ifPresent(imp -> imp.setName("org.eclipse.jetty.server.ServerConnector"));
    }
}
