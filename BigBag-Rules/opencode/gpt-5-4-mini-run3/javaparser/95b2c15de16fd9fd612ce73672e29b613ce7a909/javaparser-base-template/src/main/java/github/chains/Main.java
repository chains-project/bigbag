package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithBlockStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    private static final Set<String> REMOVED_SERVER_METHODS = new HashSet<String>(Arrays.asList(
            "setSendServerVersion",
            "setSendDateHeader"
    ));

    public static void main(String[] args) {
        Path inputRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get("/workspace/jadler");
        try {
            List<Path> javaFiles = Files.walk(inputRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transformFile(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + inputRoot, e);
        }
    }

    private static void transformFile(Path file) throws IOException {
        String original = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(original);

        boolean changed = false;
        changed |= rewriteImports(cu);
        changed |= rewriteServletImports(cu);
        changed |= rewriteRemovedServerMethods(cu);
        changed |= rewriteOldConnectors(cu);

        if (changed) {
            Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        ensureImport(cu, "org.eclipse.jetty.server.HttpConfiguration");
        ensureImport(cu, "org.eclipse.jetty.server.HttpConnectionFactory");
        ensureImport(cu, "org.eclipse.jetty.server.ServerConnector");
        return trueOrChanged(cu, changed);
    }

    private static boolean rewriteServletImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration id : cu.getImports()) {
            String name = id.getNameAsString();
            if (name.startsWith("javax.servlet")) {
                id.setName(name.replaceFirst("^javax\\.servlet", "jakarta.servlet"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteRemovedServerMethods(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!REMOVED_SERVER_METHODS.contains(call.getNameAsString())) {
                continue;
            }

            Optional<BlockStmt> block = call.findAncestor(BlockStmt.class);
            if (!block.isPresent()) {
                continue;
            }

            String configName = ensureHttpConfiguration(block.get());
            call.setScope(new NameExpr(configName));
            changed = true;
        }
        return changed;
    }

    private static boolean rewriteOldConnectors(CompilationUnit cu) {
        boolean changed = false;

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!isOldConnectorCreation(creation)) {
                continue;
            }

            Optional<BlockStmt> block = creation.findAncestor(BlockStmt.class);
            if (!block.isPresent()) {
                continue;
            }

            String configName = ensureHttpConfiguration(block.get());
            Expression serverExpression = resolveServerExpression(creation);

            creation.setType(new ClassOrInterfaceType(null, "ServerConnector"));
            NodeList<com.github.javaparser.ast.expr.Expression> args = new NodeList<com.github.javaparser.ast.expr.Expression>();
            args.add(serverExpression);
            args.add(new ObjectCreationExpr(null,
                    new ClassOrInterfaceType(null, "HttpConnectionFactory"),
                    NodeList.nodeList(new NameExpr(configName))));
            creation.setArguments(args);

            updateDeclaredConnectorType(cu, creation);
            changed = true;
        }

        return changed;
    }

    private static boolean isOldConnectorCreation(ObjectCreationExpr creation) {
        String typeName = creation.getType().getNameAsString();
        return "SelectChannelConnector".equals(typeName)
                || typeName.endsWith("SelectChannelConnector");
    }

    private static String ensureHttpConfiguration(BlockStmt block) {
        Optional<VariableDeclarator> existing = block.findAll(VariableDeclarator.class).stream()
                .filter(v -> "HttpConfiguration".equals(v.getType().asString()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().getNameAsString();
        }

        String name = reserveName(block, "httpConfiguration");
        Statement declaration = StaticJavaParser.parseStatement("HttpConfiguration " + name + " = new HttpConfiguration();");
        block.getStatements().addFirst(declaration);
        return name;
    }

    private static String reserveName(BlockStmt block, String baseName) {
        Set<String> names = block.findAll(VariableDeclarator.class).stream()
                .map(VariableDeclarator::getNameAsString)
                .collect(Collectors.toSet());
        if (!names.contains(baseName)) {
            return baseName;
        }
        int i = 2;
        while (names.contains(baseName + i)) {
            i++;
        }
        return baseName + i;
    }

    private static Expression resolveServerExpression(ObjectCreationExpr creation) {
        Optional<BlockStmt> block = creation.findAncestor(BlockStmt.class);
        if (!block.isPresent()) {
            return new NameExpr("server");
        }

        List<VariableDeclarator> serverVariables = block.get().findAll(VariableDeclarator.class).stream()
                .filter(v -> "Server".equals(simpleTypeName(v.getType())))
                .collect(Collectors.toList());
        for (VariableDeclarator variable : serverVariables) {
            if ("server".equals(variable.getNameAsString())) {
                return new NameExpr(variable.getNameAsString());
            }
        }

        Optional<ClassOrInterfaceDeclaration> clazz = creation.findAncestor(ClassOrInterfaceDeclaration.class);
        if (clazz.isPresent()) {
            for (FieldDeclaration field : clazz.get().getFields()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    if ("Server".equals(simpleTypeName(variable.getType())) && "server".equals(variable.getNameAsString())) {
                        return new FieldAccessExpr(new ThisExpr(), variable.getNameAsString());
                    }
                }
            }
        }

        if (!serverVariables.isEmpty()) {
            VariableDeclarator variable = serverVariables.get(0);
            return new NameExpr(variable.getNameAsString());
        }

        return new NameExpr("server");
    }

    private static void updateDeclaredConnectorType(CompilationUnit cu, ObjectCreationExpr creation) {
        Optional<com.github.javaparser.ast.expr.Expression> target = findConnectorAssignmentTarget(creation);
        if (!target.isPresent()) {
            Optional<VariableDeclarator> variable = creation.findAncestor(VariableDeclarator.class);
            if (variable.isPresent()) {
                updateTypeIfConnector(variable.get().getType());
            }
            return;
        }

        String name = target.get().toString();
        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            if (name.equals(variable.getNameAsString())) {
                updateTypeIfConnector(variable.getType());
            }
        }
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator variable : field.getVariables()) {
                if (name.equals(variable.getNameAsString())) {
                    updateTypeIfConnector(variable.getType());
                }
            }
        }
    }

    private static Optional<com.github.javaparser.ast.expr.Expression> findConnectorAssignmentTarget(ObjectCreationExpr creation) {
        if (creation.getParentNode().isPresent() && creation.getParentNode().get() instanceof com.github.javaparser.ast.expr.AssignExpr) {
            com.github.javaparser.ast.expr.AssignExpr assign = (com.github.javaparser.ast.expr.AssignExpr) creation.getParentNode().get();
            return Optional.of(assign.getTarget());
        }
        return Optional.empty();
    }

    private static void updateTypeIfConnector(com.github.javaparser.ast.type.Type type) {
        if (type.isClassOrInterfaceType() && "Connector".equals(simpleTypeName(type))) {
            type.asClassOrInterfaceType().setName("ServerConnector");
        }
    }

    private static String simpleTypeName(com.github.javaparser.ast.type.Type type) {
        if (!type.isClassOrInterfaceType()) {
            return type.asString();
        }
        return type.asClassOrInterfaceType().getNameAsString();
    }

    private static void ensureImport(CompilationUnit cu, String fqcn) {
        boolean present = cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(fqcn));
        if (!present) {
            cu.addImport(fqcn);
        }
    }

    private static boolean trueOrChanged(CompilationUnit cu, boolean changed) {
        return changed || !cu.getImports().isEmpty();
    }
}
