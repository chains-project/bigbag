package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteBlocks(cu);
            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        List<ImportDeclaration> imports = new ArrayList<>(cu.getImports());
        for (ImportDeclaration imp : imports) {
            String name = imp.getNameAsString();
            if (name.equals(OLD_CONNECTOR)) {
                cu.getImports().remove(imp);
                cu.addImport(NEW_CONNECTOR);
                changed = true;
            } else if (name.startsWith("javax.servlet")) {
                cu.getImports().remove(imp);
                cu.addImport(name.replace("javax.servlet", "jakarta.servlet"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteBlocks(CompilationUnit cu) {
        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= rewriteServerConfiguration(block);
            changed |= rewriteConnectorCreations(block);
        }
        return changed;
    }

    private static boolean rewriteServerConfiguration(BlockStmt block) {
        List<MethodCallExpr> calls = block.findAll(MethodCallExpr.class).stream()
                .filter(Main::isOldServerConfigCall)
                .filter(call -> call.findAncestor(BlockStmt.class).orElse(null) == block)
                .toList();

        if (calls.isEmpty()) {
            return false;
        }

        Expression serverScope = calls.get(0).getScope().orElse(null);
        if (serverScope == null) {
            return false;
        }

        String configName = uniqueName(block, "httpConfiguration");
        Statement declaration = StaticJavaParser.parseStatement("final HttpConfiguration " + configName + " = new HttpConfiguration();");
        Statement anchor = calls.get(0).findAncestor(Statement.class).orElse(null);
        if (anchor != null) {
            block.addStatement(block.getStatements().indexOf(anchor), declaration);
        }

        ensureImport(block, HTTP_CONFIGURATION);
        ensureImport(block, HTTP_CONNECTION_FACTORY);

        for (MethodCallExpr call : calls) {
            call.setScope(new NameExpr(configName));
        }

        for (ObjectCreationExpr creation : block.findAll(ObjectCreationExpr.class)) {
            if (creation.findAncestor(BlockStmt.class).orElse(null) != block) {
                continue;
            }
            if (isOldConnectorCreation(creation)) {
                rewriteConnectorCreation(creation, serverScope, configName);
            }
        }

        return true;
    }

    private static boolean rewriteConnectorCreations(BlockStmt block) {
        boolean changed = false;
        Optional<Expression> serverScope = findServerScope(block);

        for (ObjectCreationExpr creation : block.findAll(ObjectCreationExpr.class)) {
            if (creation.findAncestor(BlockStmt.class).orElse(null) != block) {
                continue;
            }
            if (isOldConnectorCreation(creation) && serverScope.isPresent()) {
                rewriteConnectorCreation(creation, serverScope.get(), null);
                changed = true;
            }
        }

        return changed;
    }

    private static void rewriteConnectorCreation(ObjectCreationExpr creation, Expression serverScope, String configName) {
        creation.setType(new ClassOrInterfaceType(null, "ServerConnector"));
        NodeList<Expression> args = new NodeList<>();
        args.add(serverScope.clone());
        if (configName != null) {
            args.add(new ObjectCreationExpr(null,
                    new ClassOrInterfaceType(null, "HttpConnectionFactory"),
                    NodeList.nodeList(new NameExpr(configName))));
        }
        creation.setArguments(args);
    }

    private static boolean isOldServerConfigCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        return name.equals("setSendServerVersion") || name.equals("setSendDateHeader");
    }

    private static boolean isOldConnectorCreation(ObjectCreationExpr creation) {
        return creation.getType().getNameAsString().equals("SelectChannelConnector");
    }

    private static Optional<Expression> findServerScope(BlockStmt block) {
        return block.findAll(MethodCallExpr.class).stream()
                .filter(Main::isOldServerConfigCall)
                .map(MethodCallExpr::getScope)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .or(() -> block.findAll(ObjectCreationExpr.class).stream()
                        .filter(Main::isOldConnectorCreation)
                        .map(creation -> creation.getArguments().isEmpty() ? null : creation.getArguments().get(0))
                        .filter(expr -> expr != null)
                        .findFirst());
    }

    private static void ensureImport(BlockStmt block, String qualifiedName) {
        CompilationUnit cu = block.findCompilationUnit().orElse(null);
        if (cu == null) {
            return;
        }
        boolean present = cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(qualifiedName));
        if (!present) {
            cu.addImport(qualifiedName);
        }
    }

    private static String uniqueName(BlockStmt block, String base) {
        Set<String> names = new HashSet<>();
        block.findAll(VariableDeclarator.class).forEach(v -> names.add(v.getNameAsString()));
        String candidate = base;
        int index = 1;
        while (names.contains(candidate)) {
            candidate = base + index++;
        }
        return candidate;
    }
}
