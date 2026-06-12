package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
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
    private static final String OLD_SIMPLE_NAME = "DateMidnight";
    private static final String NEW_SIMPLE_NAME = "LocalDate";
    private static final String OLD_FQCN = "org.joda.time.DateMidnight";
    private static final String NEW_FQCN = "org.joda.time.LocalDate";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
            for (Path file : javaFiles) {
                rewriteFile(file);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        changed |= rewriteConstructorCalls(cu);
        changed |= rewriteMethodCalls(cu);
        changed |= rewriteTypesAndImports(cu);

        if (changed) {
            String source = LexicalPreservingPrinter.print(cu).replace("DateMidnight", "LocalDate");
            Files.write(file, source.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean rewriteTypesAndImports(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDecl : cu.findAll(ImportDeclaration.class)) {
            if (importDecl.getNameAsString().equals(OLD_FQCN)) {
                importDecl.setName(NEW_FQCN);
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getNameAsString().equals(OLD_SIMPLE_NAME)) {
                type.setName(NEW_SIMPLE_NAME);
                changed = true;
            }
        }

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (creation.getType().getNameAsString().equals(OLD_SIMPLE_NAME)) {
                creation.getType().setName(NEW_SIMPLE_NAME);
                changed = true;
            }
        }

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getScope().isPresent() && isStaticTypeReference(call.getScope().get(), OLD_SIMPLE_NAME, cu)) {
                call.getScope().get().ifNameExpr(name -> name.setName(NEW_SIMPLE_NAME));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteMethodCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("toGregorianCalendar".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                if (isDateMidnightExpression(scope, cu)) {
                    call.setScope(new MethodCallExpr(scope.clone(), "toDateTimeAtStartOfDay"));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean rewriteConstructorCalls(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!creation.getType().getNameAsString().equals(OLD_SIMPLE_NAME) || creation.getArguments().size() != 1) {
                continue;
            }

            Expression argument = creation.getArgument(0);
            if (!argument.isNameExpr()) {
                continue;
            }
            String argName = argument.asNameExpr().getNameAsString();
            if (hasDeclarationWithType(cu, argName, "Calendar")
                || hasDeclarationWithType(cu, argName, "GregorianCalendar")) {
                creation.replace(new ObjectCreationExpr(null,
                    StaticJavaParser.parseClassOrInterfaceType(NEW_SIMPLE_NAME),
                    NodeList.nodeList(argument.clone())));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isStaticTypeReference(Expression expression, String simpleName, CompilationUnit cu) {
        if (!expression.isNameExpr()) {
            return false;
        }
        String name = expression.asNameExpr().getNameAsString();
        if (!simpleName.equals(name)) {
            return false;
        }
        return cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(OLD_FQCN) || i.getNameAsString().equals(NEW_FQCN));
    }

    private static boolean isDateMidnightExpression(Expression expression, CompilationUnit cu) {
        if (expression.isObjectCreationExpr()) {
            return expression.asObjectCreationExpr().getType().getNameAsString().equals(OLD_SIMPLE_NAME);
        }
        if (expression.isNameExpr()) {
            return isNameDeclaredAsOldType(expression.asNameExpr().getNameAsString(), expression);
        }
        return false;
    }

    private static boolean isNameDeclaredAsOldType(String name, Expression expression) {
        Optional<MethodDeclaration> method = expression.findAncestor(MethodDeclaration.class);
        if (method.isPresent() && method.get().getParameters().stream().anyMatch(p -> p.getNameAsString().equals(name) && typeMatches(p, OLD_SIMPLE_NAME))) {
            return true;
        }

        Optional<ConstructorDeclaration> constructor = expression.findAncestor(ConstructorDeclaration.class);
        if (constructor.isPresent() && constructor.get().getParameters().stream().anyMatch(p -> p.getNameAsString().equals(name) && typeMatches(p, OLD_SIMPLE_NAME))) {
            return true;
        }

        return expression.findAncestor(FieldDeclaration.class)
            .map(f -> f.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(name) && typeMatches(v, OLD_SIMPLE_NAME)))
            .orElse(false);
    }

    private static boolean hasDeclarationWithType(CompilationUnit cu, String name, String typeName) {
        Optional<Parameter> parameter = cu.findAll(Parameter.class).stream()
            .filter(p -> p.getNameAsString().equals(name) && typeMatches(p, typeName))
            .findFirst();
        if (parameter.isPresent()) {
            return true;
        }

        Optional<VariableDeclarator> variable = cu.findAll(VariableDeclarator.class).stream()
            .filter(v -> v.getNameAsString().equals(name) && typeMatches(v, typeName))
            .findFirst();
        if (variable.isPresent()) {
            return true;
        }

        return cu.findAll(FieldDeclaration.class).stream()
            .flatMap(f -> f.getVariables().stream())
            .anyMatch(v -> v.getNameAsString().equals(name) && typeMatches(v, typeName));
    }

    private static boolean typeMatches(NodeWithType<?, ?> node, String typeName) {
        return node.getType().isClassOrInterfaceType()
            && node.getType().asClassOrInterfaceType().getNameAsString().equals(typeName);
    }
}
