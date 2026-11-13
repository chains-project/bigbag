package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        StaticJavaParser.setConfiguration(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        try {
            Files.walk(sourceRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = rewriteImports(cu) | rewriteBodies(cu);
            if (changed) {
                Files.writeString(path, cu.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        changed |= replaceImportPrefix(cu, "javax.servlet.", "jakarta.servlet.");
        changed |= replaceImport(cu, "org.eclipse.jetty.server.nio.SelectChannelConnector", "org.eclipse.jetty.server.ServerConnector");
        if (needsImport(cu, "org.eclipse.jetty.server.HttpConfiguration")) {
            cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
            changed = true;
        }
        if (needsImport(cu, "org.eclipse.jetty.server.HttpConnectionFactory")) {
            cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
            changed = true;
        }
        if (needsImport(cu, "org.eclipse.jetty.server.ServerConnector")) {
            cu.addImport("org.eclipse.jetty.server.ServerConnector");
            changed = true;
        }
        return changed;
    }

    private static boolean replaceImportPrefix(CompilationUnit cu, String oldPrefix, String newPrefix) {
        boolean changed = false;
        for (int i = 0; i < cu.getImports().size(); i++) {
            String name = cu.getImports().get(i).getNameAsString();
            if (name.startsWith(oldPrefix)) {
                cu.getImports().get(i).setName(newPrefix + name.substring(oldPrefix.length()));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceImport(CompilationUnit cu, String oldName, String newName) {
        boolean changed = false;
        for (int i = 0; i < cu.getImports().size(); i++) {
            if (cu.getImports().get(i).getNameAsString().equals(oldName)) {
                cu.getImports().get(i).setName(newName);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean needsImport(CompilationUnit cu, String name) {
        return cu.getImports().stream().noneMatch(importDeclaration -> importDeclaration.getNameAsString().equals(name));
    }

    private static boolean rewriteBodies(CompilationUnit cu) {
        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= rewriteBlock(block);
        }
        return changed;
    }

    private static boolean rewriteBlock(BlockStmt block) {
        boolean changed = false;
        boolean hasLegacyConnector = block.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::isLegacyConnectorCreation)
                || block.findAll(FieldDeclaration.class).stream().anyMatch(Main::isLegacyConnectorField)
                || block.findAll(VariableDeclarator.class).stream().anyMatch(Main::isLegacyConnectorVariable);
        boolean hasLegacyServerConfig = block.findAll(MethodCallExpr.class).stream().anyMatch(Main::isLegacyServerConfigCall);

        if (hasLegacyServerConfig && !hasHttpConfigurationDeclaration(block)) {
            block.addStatement(firstRelevantStatementIndex(block), StaticJavaParser.parseStatement("HttpConfiguration httpConfiguration = new HttpConfiguration();"));
            changed = true;
        }

        for (MethodCallExpr call : block.findAll(MethodCallExpr.class)) {
            if (isLegacyServerConfigCall(call)) {
                call.setScope(new NameExpr("httpConfiguration"));
                changed = true;
            }
        }

        for (ObjectCreationExpr creation : block.findAll(ObjectCreationExpr.class)) {
            if (isLegacyConnectorCreation(creation)) {
                creation.setType("ServerConnector");
                creation.getArguments().clear();
                creation.addArgument(new NameExpr(findServerName(creation).orElse("server")));
                creation.addArgument(new ObjectCreationExpr().setType("HttpConnectionFactory").addArgument(new NameExpr("httpConfiguration")));
                changed = true;
            }
        }

        for (VariableDeclarator variable : block.findAll(VariableDeclarator.class)) {
            if (isLegacyConnectorVariable(variable)) {
                variable.setType("ServerConnector");
                changed = true;
            }
        }

        for (FieldDeclaration field : block.findAll(FieldDeclaration.class)) {
            if (isLegacyConnectorField(field)) {
                field.getVariables().forEach(variable -> variable.setType("ServerConnector"));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean hasHttpConfigurationDeclaration(BlockStmt block) {
        return block.findAll(VariableDeclarator.class).stream()
                .anyMatch(variable -> variable.getType().asString().equals("HttpConfiguration") || variable.getType().asString().equals("org.eclipse.jetty.server.HttpConfiguration"));
    }

    private static boolean isLegacyServerConfigCall(MethodCallExpr call) {
        return ("setSendServerVersion".equals(call.getNameAsString()) || "setSendDateHeader".equals(call.getNameAsString()))
                && call.getArguments().size() == 1;
    }

    private static boolean isLegacyConnectorCreation(ObjectCreationExpr creation) {
        return creation.getType().getNameAsString().equals("SelectChannelConnector")
                || creation.getType().asString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
    }

    private static boolean isLegacyConnectorVariable(VariableDeclarator variable) {
        if (!isConnectorType(variable.getType().asString())) {
            return false;
        }
        if (variable.getInitializer().filter(Expression::isObjectCreationExpr).map(Expression::asObjectCreationExpr).filter(Main::isLegacyConnectorCreation).isPresent()) {
            return true;
        }
        return variable.findAncestor(BlockStmt.class)
                .map(block -> block.findAll(MethodCallExpr.class).stream().anyMatch(call -> callTargetsName(call, variable.getNameAsString())
                        && ("setPort".equals(call.getNameAsString()) || "getLocalPort".equals(call.getNameAsString()))))
                .orElse(false);
    }

    private static boolean isLegacyConnectorField(FieldDeclaration field) {
        return field.getVariables().stream().anyMatch(variable -> isConnectorType(variable.getType().asString())
                && (variable.getInitializer().filter(Expression::isObjectCreationExpr).map(Expression::asObjectCreationExpr).filter(Main::isLegacyConnectorCreation).isPresent()
                || field.getParentNode().flatMap(parent -> parent.findAll(MethodCallExpr.class).stream().filter(call -> callTargetsName(call, variable.getNameAsString())
                        && ("setPort".equals(call.getNameAsString()) || "getLocalPort".equals(call.getNameAsString()))).findFirst()).isPresent()));
    }

    private static boolean isConnectorType(String typeName) {
        return "Connector".equals(typeName) || "org.eclipse.jetty.server.Connector".equals(typeName);
    }

    private static boolean callTargetsName(MethodCallExpr call, String name) {
        return call.getScope().map(scope -> scope.isNameExpr() && scope.asNameExpr().getNameAsString().equals(name)
                || scope.isFieldAccessExpr() && scope.asFieldAccessExpr().getNameAsString().equals(name)).orElse(false);
    }

    private static int firstRelevantStatementIndex(BlockStmt block) {
        List<Statement> statements = block.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (statement.findFirst(MethodCallExpr.class, Main::isLegacyServerConfigCall).isPresent()
                    || statement.findFirst(ObjectCreationExpr.class, Main::isLegacyConnectorCreation).isPresent()) {
                return i;
            }
        }
        return 0;
    }

    private static Optional<String> findServerName(Node node) {
        return node.findAncestor(BlockStmt.class)
                .flatMap(block -> block.findAll(VariableDeclarator.class).stream()
                        .filter(variable -> "Server".equals(variable.getType().asString()) || "org.eclipse.jetty.server.Server".equals(variable.getType().asString()))
                        .sorted(Comparator.comparingInt(variable -> variable.getRange().map(range -> range.begin.line).orElse(Integer.MAX_VALUE)))
                        .map(VariableDeclarator::getNameAsString)
                        .findFirst());
    }
}
