package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root");
        }

        Path root = Path.of(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }

        try {
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(Main::rewriteIfNeeded);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteIfNeeded(Path file) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(file);
            boolean changed = false;

            for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
                changed |= rewriteObjectCreation(creation);
            }

            for (ConstructorDeclaration constructor : unit.findAll(ConstructorDeclaration.class)) {
                changed |= rewriteRepresenterConstructor(constructor);
            }

            for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
                changed |= rewriteRepresenterGetProperties(method);
            }

            for (ExplicitConstructorInvocationStmt invocation : unit.findAll(ExplicitConstructorInvocationStmt.class)) {
                changed |= rewriteExplicitConstructorInvocation(unit, invocation);
            }

            if (changed) {
                Files.writeString(file, unit.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteObjectCreation(ObjectCreationExpr creation) {
        String typeName = creation.getType().asString();
        int argCount = creation.getArguments().size();

        if (matches(typeName, "org.yaml.snakeyaml.representer.Representer") && argCount == 0) {
            creation.setArguments(singletonArgs("new org.yaml.snakeyaml.DumperOptions()"));
            return true;
        }

        if (matches(typeName, "org.yaml.snakeyaml.constructor.Constructor") && argCount == 1
                && creation.getArgument(0).isClassExpr()) {
            creation.setArguments(singletonArgs("new org.yaml.snakeyaml.LoaderOptions()"));
            return true;
        }

        if (matches(typeName, "org.yaml.snakeyaml.constructor.SafeConstructor") && argCount == 0) {
            creation.addArgument(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
            return true;
        }

        if (matches(typeName, "org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor") && argCount == 1) {
            creation.addArgument(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
            return true;
        }

        if (matches(typeName, "org.yaml.snakeyaml.Yaml") && argCount == 4) {
            creation.addArgument(3, StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
            return true;
        }

        return false;
    }

    private static boolean rewriteRepresenterConstructor(ConstructorDeclaration constructor) {
        if (!constructor.getParameters().isEmpty()) {
            return false;
        }

        ClassOrInterfaceDeclaration owner = constructor.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner == null || owner.getExtendedTypes().stream().noneMatch(type -> matches(type.asString(), "org.yaml.snakeyaml.representer.Representer"))) {
            return false;
        }

        BlockStmt body = constructor.getBody();
        if (!body.getStatements().isEmpty() && body.getStatement(0) instanceof ExplicitConstructorInvocationStmt) {
            return false;
        }

        BlockStmt rewritten = new BlockStmt();
        rewritten.addStatement(parseConstructorInvocation("super(new org.yaml.snakeyaml.DumperOptions())"));
        body.getStatements().forEach(stmt -> rewritten.addStatement(stmt.clone()));
        constructor.setBody(rewritten);
        return true;
    }

    private static boolean rewriteRepresenterGetProperties(MethodDeclaration method) {
        if (!method.getNameAsString().equals("getProperties") || method.getParameters().size() != 1) {
            return false;
        }

        ClassOrInterfaceDeclaration owner = method.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner == null || owner.getExtendedTypes().stream().noneMatch(type -> matches(type.asString(), "org.yaml.snakeyaml.representer.Representer"))) {
            return false;
        }

        return removeIntrospectionExceptionThrows(method);
    }

    private static boolean rewriteExplicitConstructorInvocation(CompilationUnit unit, ExplicitConstructorInvocationStmt invocation) {
        if (invocation.isThis() || invocation.getArguments().isEmpty()) {
            return false;
        }

        ClassOrInterfaceDeclaration owner = invocation.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner == null) {
            return false;
        }

        boolean extendsSnakeYamlConstructor = owner.getExtendedTypes().stream().anyMatch(type ->
                matches(type.asString(), "org.yaml.snakeyaml.constructor.Constructor")
                        || matches(type.asString(), "org.yaml.snakeyaml.constructor.SafeConstructor")
                        || matches(type.asString(), "org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor")
                        || matches(type.asString(), "org.yaml.snakeyaml.constructor.BaseConstructor"));

        if (!extendsSnakeYamlConstructor) {
            return false;
        }

        NodeList<Expression> args = invocation.getArguments();
        if (owner.getExtendedTypes().stream().anyMatch(type -> matches(type.asString(), "org.yaml.snakeyaml.constructor.Constructor"))
                && args.size() == 1 && args.get(0).isClassExpr()) {
            invocation.setArguments(singletonArgs("new org.yaml.snakeyaml.LoaderOptions()"));
            return true;
        }

        if (owner.getExtendedTypes().stream().anyMatch(type -> matches(type.asString(), "org.yaml.snakeyaml.constructor.SafeConstructor"))
                && args.isEmpty()) {
            args.add(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
            return true;
        }

        if (owner.getExtendedTypes().stream().anyMatch(type -> matches(type.asString(), "org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor"))
                && args.size() == 1) {
            args.add(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
            return true;
        }

        if (owner.getExtendedTypes().stream().anyMatch(type -> matches(type.asString(), "org.yaml.snakeyaml.constructor.BaseConstructor"))
                && args.size() == 4) {
            args.add(3, StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
            return true;
        }

        return false;
    }

    private static NodeList<Expression> singletonArgs(String expression) {
        NodeList<Expression> args = new NodeList<>();
        args.add(StaticJavaParser.parseExpression(expression));
        return args;
    }

    private static ExplicitConstructorInvocationStmt parseConstructorInvocation(String statement) {
        CompilationUnit unit = StaticJavaParser.parse("class __X { __X() { " + statement + "; } }");
        return unit.findFirst(ConstructorDeclaration.class)
                .orElseThrow(() -> new IllegalStateException("Failed to parse constructor invocation"))
                .getBody()
                .getStatement(0)
                .asExplicitConstructorInvocationStmt();
    }

    private static boolean removeIntrospectionExceptionThrows(MethodDeclaration method) {
        NodeList<ReferenceType> filtered = new NodeList<>();
        boolean changed = false;
        for (ReferenceType thrown : method.getThrownExceptions()) {
            if (thrown.asString().endsWith("IntrospectionException")) {
                changed = true;
            } else {
                filtered.add(thrown);
            }
        }
        if (changed) {
            method.setThrownExceptions(filtered);
        }
        return changed;
    }

    private static boolean matches(String actualType, String expectedFqn) {
        String expectedSimpleName = expectedFqn.substring(expectedFqn.lastIndexOf('.') + 1);
        return actualType.equals(expectedFqn) || actualType.equals(expectedSimpleName) || actualType.endsWith("." + expectedSimpleName);
    }
}
