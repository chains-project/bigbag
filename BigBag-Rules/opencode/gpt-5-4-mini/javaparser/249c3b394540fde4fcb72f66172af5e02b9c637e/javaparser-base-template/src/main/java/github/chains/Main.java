package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source root: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                if (isOldSnakeYamlYamlConstructor(expr)) {
                    if (expr.getArguments().size() == 4) {
                        expr.getArguments().add(3, new ObjectCreationExpr(
                            null,
                            StaticJavaParser.parseClassOrInterfaceType("org.yaml.snakeyaml.LoaderOptions"),
                            new NodeList<>()));
                        changed = true;
                    }
                }
            }

            for (VariableDeclarationExpr declaration : cu.findAll(VariableDeclarationExpr.class)) {
                if (isDumperOptionsDeclaration(declaration) && injectDumperOptionsDefaults(declaration)) {
                    changed = true;
                }
            }

            for (ClassOrInterfaceDeclaration declaration : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (extendsSnakeYamlRepresenter(declaration)) {
                    for (ConstructorDeclaration constructor : declaration.getConstructors()) {
                        if (prependDefaultScalarStyle(cu, constructor.getBody(), declaration.getNameAsString())) {
                            changed = true;
                        }
                    }
                    for (MethodDeclaration method : declaration.getMethods()) {
                        if (isOldSnakeYamlRepresenterOverride(method)
                                && method.getThrownExceptions().removeIf(type ->
                                "java.beans.IntrospectionException".equals(type.asString())
                                        || "IntrospectionException".equals(type.asString()))) {
                            changed = true;
                        }
                    }
                }
            }

            if (changed) {
                Files.writeString(path, cu.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean isOldSnakeYamlYamlConstructor(ObjectCreationExpr expr) {
        return "Yaml".equals(expr.getType().getNameAsString())
                && expr.getArguments().size() == 4;
    }

    private static boolean extendsSnakeYamlRepresenter(ClassOrInterfaceDeclaration declaration) {
        return declaration.getExtendedTypes().stream()
                .anyMatch(type -> "Representer".equals(type.getNameAsString())
                        || "org.yaml.snakeyaml.representer.Representer".equals(type.asString()));
    }

    private static boolean isOldSnakeYamlRepresenterOverride(MethodDeclaration method) {
        return "getProperties".equals(method.getNameAsString())
                && method.getParameters().size() == 1
                && method.getParameters().get(0).getType().isClassOrInterfaceType()
                && "Class".equals(method.getParameters().get(0).getType().asClassOrInterfaceType().getNameAsString())
                && method.getThrownExceptions().stream().anyMatch(type ->
                "java.beans.IntrospectionException".equals(type.asString())
                        || "IntrospectionException".equals(type.asString()));
    }

    private static boolean prependDefaultScalarStyle(CompilationUnit cu, BlockStmt body, String className) {
        boolean changed = false;
        String scalarSnippet = "setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN)";
        if (!body.toString().contains(scalarSnippet)) {
            body.addStatement(0, StaticJavaParser.parseStatement("this.setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN);"));
            changed = true;
        }

        String flowSnippet = "setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK)";
        if (!body.toString().contains(flowSnippet)) {
            body.addStatement(1, StaticJavaParser.parseStatement("this.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);"));
            changed = true;
        }

        return changed;
    }

    private static boolean isDumperOptionsDeclaration(VariableDeclarationExpr declaration) {
        return declaration.getVariables().size() == 1
                && declaration.getVariable(0).getInitializer().isPresent()
                && declaration.getVariable(0).getInitializer().get().isObjectCreationExpr()
                && "DumperOptions".equals(declaration.getVariable(0).getInitializer().get()
                .asObjectCreationExpr().getType().getNameAsString());
    }

    private static boolean injectDumperOptionsDefaults(VariableDeclarationExpr declaration) {
        if (!(declaration.getParentNode().orElse(null) instanceof ExpressionStmt)) {
            return false;
        }

        ExpressionStmt stmt = (ExpressionStmt) declaration.getParentNode().get();
        if (!(stmt.getParentNode().orElse(null) instanceof BlockStmt)) {
            return false;
        }

        BlockStmt body = (BlockStmt) stmt.getParentNode().get();
        String var = declaration.getVariable(0).getNameAsString();
        String scalar = var + ".setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN);";
        String flow = var + ".setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);";
        int idx = body.getStatements().indexOf(stmt);
        if (idx < 0) {
            return false;
        }

        boolean changed = false;
        if (!body.toString().contains(scalar)) {
            body.addStatement(idx + 1, StaticJavaParser.parseStatement(scalar));
            changed = true;
            idx++;
        }
        if (!body.toString().contains(flow)) {
            body.addStatement(idx + 1, StaticJavaParser.parseStatement(flow));
            changed = true;
        }
        return changed;
    }
}
