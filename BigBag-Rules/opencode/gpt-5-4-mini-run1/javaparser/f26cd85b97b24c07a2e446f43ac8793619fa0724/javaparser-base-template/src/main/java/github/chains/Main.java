package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
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
    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transformFile(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + root, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);
            compilationUnit.accept(new Jetty11MigrationVisitor(), null);
            Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static final class Jetty11MigrationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(CompilationUnit n, Void arg) {
            n.getImports().forEach(Main::rewriteImport);
            if (n.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::isSelectChannelConnectorCreation)
                    || n.findAll(MethodCallExpr.class).stream().anyMatch(Main::isServerConfigurationCall)) {
                ensureImport(n, "org.eclipse.jetty.server.HttpConfiguration");
                ensureImport(n, "org.eclipse.jetty.server.HttpConnectionFactory");
                ensureImport(n, "org.eclipse.jetty.server.ServerConnector");
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(BlockStmt n, Void arg) {
            super.visit(n, arg);

            String httpConfigurationName = findExistingHttpConfigurationName(n).orElse(null);
            boolean needsHttpConfiguration = containsJetty11ConfigurationCalls(n) || containsSelectChannelConnectorCreation(n);
            if (needsHttpConfiguration && httpConfigurationName == null) {
                httpConfigurationName = uniqueName(n, "httpConfiguration");
                n.addStatement(insertIndexForBlock(n), createHttpConfigurationDeclaration(httpConfigurationName));
            }

            if (httpConfigurationName != null) {
                rewriteConfigurationCalls(n, httpConfigurationName);
            }

            rewriteConnectorCreations(n, httpConfigurationName);
            rewriteConnectorVariableTypes(n);
            return n;
        }

        @Override
        public Visitable visit(VariableDeclarator n, Void arg) {
            super.visit(n, arg);
            if (n.getInitializer().isPresent() && isSelectChannelConnectorCreation(n.getInitializer().get())) {
                n.setType(new ClassOrInterfaceType(null, "ServerConnector"));
            }
            return n;
        }

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (isJettyServerPortCall(n)) {
                promoteConnectorDeclaration(n);
            }
            return n;
        }

        private void rewriteConfigurationCalls(BlockStmt block, String httpConfigurationName) {
            block.findAll(MethodCallExpr.class).forEach(call -> {
                if (isServerConfigurationCall(call)) {
                    call.setScope(new NameExpr(httpConfigurationName));
                }
            });
        }

        private void rewriteConnectorCreations(BlockStmt block, String httpConfigurationName) {
            block.findAll(ObjectCreationExpr.class).forEach(expr -> {
                if (!isSelectChannelConnectorCreation(expr)) {
                    return;
                }

                Expression serverExpression = resolveServerExpression(expr).orElseGet(() -> new NameExpr("server"));
                NodeList<Expression> arguments = new NodeList<>();
                arguments.add(serverExpression);
                if (httpConfigurationName != null) {
                    arguments.add(new ObjectCreationExpr(null,
                            new ClassOrInterfaceType(null, "HttpConnectionFactory"),
                            new NodeList<>(new NameExpr(httpConfigurationName))));
                }

                expr.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                expr.setArguments(arguments);
            });
        }

        private void rewriteConnectorVariableTypes(BlockStmt block) {
            block.findAll(MethodCallExpr.class).stream()
                    .filter(Main::isJettyServerPortCall)
                    .forEach(this::promoteConnectorDeclaration);
        }

        private void promoteConnectorDeclaration(MethodCallExpr call) {
            Optional<String> referencedName = referencedName(call.getScope().orElse(null));
            if (!referencedName.isPresent()) {
                return;
            }

            String name = referencedName.get();
            Optional<VariableDeclarator> local = call.findAncestor(BlockStmt.class)
                    .flatMap(block -> block.findAll(VariableDeclarator.class).stream()
                            .filter(v -> v.getNameAsString().equals(name))
                            .filter(v -> isTypeNamed(v.getType(), "Connector"))
                            .findFirst());
            if (local.isPresent()) {
                local.get().setType(new ClassOrInterfaceType(null, "ServerConnector"));
                return;
            }

            call.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(type -> type.getFields().stream()
                    .filter(field -> field.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(name)))
                    .filter(field -> isFieldTypeNamed(field, "Connector"))
                    .forEach(field -> field.getVariables().forEach(v -> v.setType(new ClassOrInterfaceType(null, "ServerConnector")))));
        }
    }

    private static void rewriteImport(com.github.javaparser.ast.ImportDeclaration importDeclaration) {
        String name = importDeclaration.getNameAsString();
        if (name.startsWith("javax.servlet")) {
            importDeclaration.setName(name.replaceFirst("^javax\\.servlet", "jakarta.servlet"));
        } else if (name.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
            importDeclaration.setName("org.eclipse.jetty.server.ServerConnector");
        }
    }

    private static void ensureImport(CompilationUnit unit, String fqcn) {
        boolean present = unit.getImports().stream().anyMatch(i -> !i.isAsterisk() && i.getNameAsString().equals(fqcn));
        if (!present) {
            unit.addImport(fqcn);
        }
    }

    private static boolean containsSelectChannelConnectorCreation(BlockStmt block) {
        return block.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::isSelectChannelConnectorCreation);
    }

    private static boolean containsJetty11ConfigurationCalls(BlockStmt block) {
        return block.findAll(MethodCallExpr.class).stream().anyMatch(Main::isServerConfigurationCall);
    }

    private static boolean isServerConfigurationCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        return "setSendServerVersion".equals(name) || "setSendDateHeader".equals(name);
    }

    private static boolean isJettyServerPortCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        return "setPort".equals(name) || "getLocalPort".equals(name);
    }

    private static boolean isSelectChannelConnectorCreation(ObjectCreationExpr expr) {
        return expr.getType().getNameAsString().equals("SelectChannelConnector");
    }

    private static boolean isSelectChannelConnectorCreation(Expression expr) {
        return expr.isObjectCreationExpr() && isSelectChannelConnectorCreation(expr.asObjectCreationExpr());
    }

    private static Optional<String> findExistingHttpConfigurationName(BlockStmt block) {
        return block.findAll(VariableDeclarator.class).stream()
                .filter(v -> isTypeNamed(v.getType(), "HttpConfiguration"))
                .map(VariableDeclarator::getNameAsString)
                .findFirst();
    }

    private static Statement createHttpConfigurationDeclaration(String name) {
        VariableDeclarator variableDeclarator = new VariableDeclarator(
                new ClassOrInterfaceType(null, "HttpConfiguration"),
                name);
        variableDeclarator.setInitializer(new ObjectCreationExpr(null,
                new ClassOrInterfaceType(null, "HttpConfiguration"),
                new NodeList<>()));
        return new com.github.javaparser.ast.stmt.ExpressionStmt(
                new com.github.javaparser.ast.expr.VariableDeclarationExpr(variableDeclarator));
    }

    private static int insertIndexForBlock(BlockStmt block) {
        List<Statement> statements = block.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (!(statement instanceof ExplicitConstructorInvocationStmt) && containsJetty11Target(statement)) {
                return i;
            }
        }
        return statements.isEmpty() ? 0 : Math.min(1, statements.size());
    }

    private static boolean containsJetty11Target(Statement statement) {
        return statement.findAll(MethodCallExpr.class).stream().anyMatch(Main::isServerConfigurationCall)
                || statement.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::isSelectChannelConnectorCreation);
    }

    private static Optional<Expression> resolveServerExpression(Node node) {
        Optional<ClassOrInterfaceDeclaration> clazz = node.findAncestor(ClassOrInterfaceDeclaration.class);
        if (clazz.isPresent()) {
            List<String> fieldCandidates = clazz.get().getFields().stream()
                    .filter(field -> isFieldTypeNamed(field, "Server"))
                    .flatMap(field -> field.getVariables().stream().map(VariableDeclarator::getNameAsString))
                    .sorted()
                    .collect(Collectors.toList());
            if (!fieldCandidates.isEmpty()) {
                if (fieldCandidates.contains("server")) {
                    return Optional.of(new NameExpr("server"));
                }
                return Optional.of(new NameExpr(fieldCandidates.get(0)));
            }
        }

        Optional<BlockStmt> block = node.findAncestor(BlockStmt.class);
        if (block.isPresent()) {
            List<String> localCandidates = block.get().findAll(VariableDeclarator.class).stream()
                    .filter(v -> isTypeNamed(v.getType(), "Server"))
                    .map(VariableDeclarator::getNameAsString)
                    .sorted()
                    .collect(Collectors.toList());
            if (!localCandidates.isEmpty()) {
                if (localCandidates.contains("server")) {
                    return Optional.of(new NameExpr("server"));
                }
                return Optional.of(new NameExpr(localCandidates.get(0)));
            }
        }

        return Optional.empty();
    }

    private static boolean isFieldTypeNamed(FieldDeclaration field, String simpleName) {
        return field.getVariables().stream().allMatch(v -> isTypeNamed(v.getType(), simpleName));
    }

    private static boolean isTypeNamed(com.github.javaparser.ast.type.Type type, String simpleName) {
        return type.isClassOrInterfaceType() && type.asClassOrInterfaceType().getNameAsString().equals(simpleName);
    }

    private static Optional<String> referencedName(Expression scope) {
        if (scope == null) {
            return Optional.empty();
        }
        if (scope.isNameExpr()) {
            return Optional.of(scope.asNameExpr().getNameAsString());
        }
        if (scope.isFieldAccessExpr()) {
            return Optional.of(scope.asFieldAccessExpr().getNameAsString());
        }
        return Optional.empty();
    }

    private static String uniqueName(Node node, String baseName) {
        if (node.findAll(VariableDeclarator.class).stream().noneMatch(v -> v.getNameAsString().equals(baseName))) {
            return baseName;
        }
        for (int i = 1; i < 100; i++) {
            String candidate = baseName + i;
            if (node.findAll(VariableDeclarator.class).stream().noneMatch(v -> v.getNameAsString().equals(candidate))) {
                return candidate;
            }
        }
        return baseName + "X";
    }
}
