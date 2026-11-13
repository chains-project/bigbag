package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SIMPLE_NAME = "ScriptResult";
    private static final String REPLACEMENT_TYPE = "Object";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        sourceRoot = sourceRoot.toAbsolutePath().normalize();

        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Main::isJavaSource)
                    .forEach(Main::rewriteFile);
        }
    }

    private static boolean isJavaSource(Path path) {
        String value = path.toString();
        return value.endsWith(".java") && !value.contains("/target/") && !value.contains("/.git/");
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = new ScriptResultTransformer().transform(compilationUnit);
            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to rewrite " + path, exception);
        }
    }

    private static final class ScriptResultTransformer extends ModifierVisitor<Void> {
        private boolean changed;

        private boolean transform(CompilationUnit compilationUnit) {
            compilationUnit.accept(this, null);
            boolean removedImport = compilationUnit.getImports().removeIf(importDeclaration ->
                    importDeclaration.getNameAsString().equals(OLD_TYPE));
            return changed || removedImport;
        }

        @Override
        public Visitable visit(ClassOrInterfaceType type, Void arg) {
            if (isOldType(type)) {
                changed = true;
                return StaticJavaParser.parseClassOrInterfaceType(REPLACEMENT_TYPE);
            }
            return super.visit(type, arg);
        }

        @Override
        public Visitable visit(VariableDeclarator declarator, Void arg) {
            if (isOldType(declarator.getType())) {
                changed = true;
                declarator.setType(REPLACEMENT_TYPE);
                declarator.getInitializer().ifPresent(initializer ->
                        declarator.setInitializer(rewriteExpression(initializer)));
                return declarator;
            }
            return super.visit(declarator, arg);
        }

        @Override
        public Visitable visit(ObjectCreationExpr creationExpr, Void arg) {
            if (isOldCreation(creationExpr)) {
                changed = true;
                return rewriteCreation(creationExpr);
            }
            return super.visit(creationExpr, arg);
        }

        @Override
        public Visitable visit(MethodCallExpr methodCallExpr, Void arg) {
            if (isGetterCall(methodCallExpr)) {
                changed = true;
                return methodCallExpr.getScope()
                        .filter(ObjectCreationExpr.class::isInstance)
                        .map(ObjectCreationExpr.class::cast)
                        .filter(this::isOldCreation)
                        .map(this::rewriteCreation)
                        .orElseGet(() -> methodCallExpr.getScope().map(this::rewriteExpression).orElse(methodCallExpr));
            }
            return super.visit(methodCallExpr, arg);
        }

        private Expression rewriteExpression(Expression expression) {
            if (expression instanceof ObjectCreationExpr && isOldCreation((ObjectCreationExpr) expression)) {
                return rewriteCreation((ObjectCreationExpr) expression);
            }
            if (expression instanceof MethodCallExpr && isGetterCall((MethodCallExpr) expression)) {
                return ((MethodCallExpr) expression).getScope().map(this::rewriteExpression).orElse(expression);
            }
            return expression.clone();
        }

        private Expression rewriteCreation(ObjectCreationExpr creationExpr) {
            List<Expression> arguments = creationExpr.getArguments();
            if (arguments.isEmpty()) {
                return creationExpr.clone();
            }
            return rewriteExpression(arguments.get(0));
        }

        private boolean isOldType(Type type) {
            if (!type.isClassOrInterfaceType()) {
                return false;
            }
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            String name = classType.toString();
            return OLD_SIMPLE_NAME.equals(classType.getNameAsString()) || OLD_TYPE.equals(name);
        }

        private boolean isOldCreation(ObjectCreationExpr creationExpr) {
            return isOldType(creationExpr.getType());
        }

        private boolean isGetterCall(MethodCallExpr methodCallExpr) {
            SimpleName name = methodCallExpr.getName();
            return "getJavaScriptResult".equals(name.asString()) && methodCallExpr.getScope().isPresent();
        }
    }
}
