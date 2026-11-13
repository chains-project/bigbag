package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transformFile(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse " + root, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            changed |= rewriteImports(cu);
            changed |= rewriteTypes(cu);
            changed |= rewriteConnectorCreations(cu);

            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        for (int i = 0; i < cu.getImports().size(); i++) {
            String name = cu.getImport(i).getNameAsString();

            if (name.equals(OLD_CONNECTOR)) {
                cu.getImports().set(i, cu.getImport(i).setName(NEW_CONNECTOR));
                changed = true;
            } else if (name.startsWith(OLD_SERVLET_PREFIX)) {
                cu.getImports().set(i, cu.getImport(i).setName(name.replaceFirst("^javax\\.servlet", "jakarta.servlet")));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteTypes(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getParentNode().filter(parent -> parent instanceof ObjectCreationExpr || parent instanceof VariableDeclarator).isPresent()) {
                continue;
            }

            String typeName = type.asString();

            if (typeName.equals(OLD_CONNECTOR) || type.getNameAsString().equals("SelectChannelConnector")) {
                type.setName("ServerConnector");
                type.removeScope();
                changed = true;
                continue;
            }

            if (typeName.startsWith(OLD_SERVLET_PREFIX)) {
                String rewritten = typeName.replaceFirst("^javax\\.servlet", "jakarta.servlet");
                if (!rewritten.equals(typeName)) {
                    Type parsed = StaticJavaParser.parseType(rewritten);
                    type.replace(parsed);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean rewriteConnectorCreations(CompilationUnit cu) {
        boolean changed = false;

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            String typeName = creation.getType().asString();
            if (!typeName.equals(OLD_CONNECTOR) && !creation.getType().getNameAsString().equals("SelectChannelConnector")) {
                continue;
            }

            Expression serverExpression = findServerExpression(creation);
            if (serverExpression == null) {
                continue;
            }

            creation.setType("ServerConnector");
            creation.setArguments(com.github.javaparser.ast.NodeList.nodeList(serverExpression.clone()));
            changed = true;
        }

        if (changed) {
            ensureImport(cu, NEW_CONNECTOR);
        }

        return changed;
    }

    private static Expression findServerExpression(ObjectCreationExpr creation) {
        Optional<String> connectorRef = findConnectorReference(creation);
        if (!connectorRef.isPresent()) {
            return null;
        }

        Optional<CallableDeclaration> callable = creation.findAncestor(CallableDeclaration.class);
        if (!callable.isPresent()) {
            return null;
        }

        return callable.get().findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("addConnector"))
                .filter(call -> call.getArguments().size() == 1)
                .filter(call -> matchesReference(call.getArgument(0), connectorRef.get()))
                .map(MethodCallExpr::getScope)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(null);
    }

    private static Optional<String> findConnectorReference(ObjectCreationExpr creation) {
        Node parent = creation.getParentNode().orElse(null);
        if (parent instanceof VariableDeclarator) {
            return Optional.of(((VariableDeclarator) parent).getNameAsString());
        }
        if (parent instanceof AssignExpr) {
            return Optional.of(((AssignExpr) parent).getTarget().toString());
        }
        return Optional.empty();
    }

    private static boolean matchesReference(Expression expression, String reference) {
        String text = expression.toString();
        return text.equals(reference) || text.equals("this." + reference);
    }

    private static void ensureImport(CompilationUnit cu, String fullyQualifiedName) {
        boolean present = cu.getImports().stream().anyMatch(importDeclaration ->
                importDeclaration.getNameAsString().equals(fullyQualifiedName));
        if (!present) {
            cu.addImport(fullyQualifiedName);
        }
    }
}
