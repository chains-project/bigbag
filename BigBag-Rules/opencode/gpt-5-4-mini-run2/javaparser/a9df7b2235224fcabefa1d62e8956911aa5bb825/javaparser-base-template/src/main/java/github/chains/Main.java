package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            System.err.println("Source root does not exist or is not a directory: " + sourceRoot);
            System.exit(1);
        }

        try {
            try (var paths = Files.walk(sourceRoot)) {
                paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path javaFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            boolean changed = false;

            List<ConstructorDeclaration> constructors = cu.findAll(ConstructorDeclaration.class);
            for (ConstructorDeclaration constructor : constructors) {
                changed |= ensureSnakeYamlRepresenterScalarStyle(constructor);
            }

            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            for (MethodDeclaration method : methods) {
                changed |= removeLegacyIntrospectionExceptionThrows(method);
            }

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                changed |= replaceNullFlowStyleArguments(call);
            }

            if (changed) {
                removeUnusedIntrospectionImport(cu);
                Files.writeString(javaFile, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static boolean removeLegacyIntrospectionExceptionThrows(MethodDeclaration method) {
        if (method.getThrownExceptions().stream().noneMatch(Main::isIntrospectionExceptionType)) {
            return false;
        }

        boolean delegatesToSnakeYamlProperties = method.getBody()
            .map(body -> body.toString().contains("super.getProperties("))
            .orElse(false);

        if (!delegatesToSnakeYamlProperties) {
            return false;
        }

        return method.getThrownExceptions().removeIf(Main::isIntrospectionExceptionType);
    }

    private static boolean replaceNullFlowStyleArguments(MethodCallExpr call) {
        String name = call.getNameAsString();
        if (!"representMapping".equals(name) && !"representSequence".equals(name)) {
            return false;
        }
        if (call.getArguments().isEmpty()) {
            return false;
        }

        Expression last = call.getArgument(call.getArguments().size() - 1);
        if (!(last instanceof NullLiteralExpr)) {
            return false;
        }

        call.setArgument(call.getArguments().size() - 1, StaticJavaParser.parseExpression("getDefaultFlowStyle()"));
        return true;
    }

    private static boolean ensureSnakeYamlRepresenterScalarStyle(ConstructorDeclaration constructor) {
        ClassOrInterfaceDeclaration owner = constructor.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner == null || owner.getExtendedTypes().isEmpty()) {
            return false;
        }

        String extendedType = owner.getExtendedTypes(0).getNameAsString();
        if (!"Representer".equals(extendedType)) {
            return false;
        }

        BlockStmt body = constructor.getBody();
        boolean changed = false;
        if (!body.toString().contains("setDefaultFlowStyle")) {
            body.addStatement(0, StaticJavaParser.parseStatement(
                "this.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.AUTO);"));
            changed = true;
        }
        if (!body.toString().contains("setDefaultScalarStyle")) {
            body.addStatement(0, StaticJavaParser.parseStatement(
                "this.setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN);"));
            changed = true;
        }
        if (!changed) {
            return false;
        }
        return true;
    }

    private static boolean isIntrospectionExceptionType(Type type) {
        String rendered = type.asString();
        return "IntrospectionException".equals(rendered)
            || "java.beans.IntrospectionException".equals(rendered);
    }

    private static void removeUnusedIntrospectionImport(CompilationUnit cu) {
        boolean stillReferenced = cu.findAll(Type.class).stream()
            .map(Type::asString)
            .anyMatch(typeName -> "IntrospectionException".equals(typeName)
                || "java.beans.IntrospectionException".equals(typeName));

        if (!stillReferenced) {
            cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals("java.beans.IntrospectionException"));
        }
    }
}
