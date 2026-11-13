package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.stream.Stream;

public class Main {
    private static final String CONSTRUCTOR_FQCN = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String REPRESENTER_FQCN = "org.yaml.snakeyaml.representer.Representer";
    private static final String LOADER_OPTIONS_EXPR = "new org.yaml.snakeyaml.LoaderOptions()";
    private static final String DUMPER_OPTIONS_EXPR = "new org.yaml.snakeyaml.DumperOptions()";

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            transform(cu);
            Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transform(CompilationUnit cu) {
        cu.findAll(ObjectCreationExpr.class).forEach(Main::rewriteObjectCreation);
        cu.findAll(ConstructorDeclaration.class).forEach(Main::rewriteConstructor);
        cu.findAll(MethodDeclaration.class).forEach(Main::rewriteMethodDeclaration);
    }

    private static void rewriteObjectCreation(ObjectCreationExpr expr) {
        if (isType(expr, CONSTRUCTOR_FQCN) && expr.getArguments().size() == 1) {
            expr.addArgument(parseExpression(LOADER_OPTIONS_EXPR));
        }
        if (isType(expr, REPRESENTER_FQCN) && expr.getArguments().isEmpty()) {
            expr.addArgument(parseExpression(DUMPER_OPTIONS_EXPR));
        }
    }

    private static void rewriteConstructor(ConstructorDeclaration constructor) {
        ClassOrInterfaceDeclaration owner = constructor.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner == null) {
            return;
        }

        boolean extendsConstructor = extendsType(owner, CONSTRUCTOR_FQCN);
        boolean extendsRepresenter = extendsType(owner, REPRESENTER_FQCN);
        if (!extendsConstructor && !extendsRepresenter) {
            return;
        }

        BlockStmt body = constructor.getBody();
        if (body.getStatements().isEmpty()) {
            if (extendsConstructor) {
                body.addStatement(0, parseStatement("super(" + LOADER_OPTIONS_EXPR + ")"));
            } else {
                body.addStatement(0, parseStatement("super(" + DUMPER_OPTIONS_EXPR + ")"));
            }
            return;
        }

        Statement first = body.getStatement(0);
        if (first.isExplicitConstructorInvocationStmt()) {
            ExplicitConstructorInvocationStmt invocation = first.asExplicitConstructorInvocationStmt();
            if (!invocation.isThis()) {
                if (extendsConstructor && invocation.getArguments().size() == 1) {
                    invocation.addArgument(parseExpression(LOADER_OPTIONS_EXPR));
                } else if (extendsRepresenter && invocation.getArguments().isEmpty()) {
                    invocation.addArgument(parseExpression(DUMPER_OPTIONS_EXPR));
                }
            }
            return;
        }

        if (extendsConstructor) {
            body.addStatement(0, parseStatement("super(" + LOADER_OPTIONS_EXPR + ")"));
        } else {
            body.addStatement(0, parseStatement("super(" + DUMPER_OPTIONS_EXPR + ")"));
        }
    }

    private static void rewriteMethodDeclaration(MethodDeclaration method) {
        ClassOrInterfaceDeclaration owner = method.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner == null || !extendsType(owner, REPRESENTER_FQCN)) {
            return;
        }

        if (method.getThrownExceptions().stream().anyMatch(t -> t.asString().endsWith("IntrospectionException"))) {
            BlockStmt body = method.getBody().orElse(null);
            if (body != null) {
                String original = body.getStatements().toString();
                String wrapped = "{ try " + body + " catch (java.beans.IntrospectionException e) { throw new RuntimeException(e); } }";
                method.setBody(StaticJavaParser.parseBlock(wrapped));
                method.getThrownExceptions().clear();
                return;
            }
        }

        if ("getProperties".equals(method.getNameAsString()) && method.getParameters().size() == 1) {
            method.getThrownExceptions().clear();
        }
    }

    private static boolean isType(ObjectCreationExpr expr, String fqcn) {
        String simple = simpleName(fqcn);
        String typeName = expr.getTypeAsString();
        return (simple.equals(typeName) || fqcn.equals(typeName)) && expr.findCompilationUnit().isPresent();
    }

    private static boolean extendsType(ClassOrInterfaceDeclaration declaration, String fqcn) {
        String simple = simpleName(fqcn);
        return declaration.getExtendedTypes().stream()
                .map(ClassOrInterfaceType::getNameAsString)
                .anyMatch(name -> name.equals(simple) || name.equals(fqcn));
    }

    private static String simpleName(String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    private static Expression parseExpression(String text) {
        return StaticJavaParser.parseExpression(text);
    }

    private static Statement parseStatement(String text) {
        return StaticJavaParser.parseStatement(text + ";");
    }
}
