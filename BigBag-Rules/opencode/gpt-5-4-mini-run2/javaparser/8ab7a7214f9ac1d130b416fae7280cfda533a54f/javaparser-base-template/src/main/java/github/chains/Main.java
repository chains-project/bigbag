package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SIMPLE_NAME = "ScriptResult";
    private static final String NEW_TYPE = "java.lang.Object";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        try {
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> rewrite(parser, path));
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void rewrite(JavaParser parser, Path path) {
        try {
            CompilationUnit cu = parser.parse(path).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            boolean hasOldImport = cu.getImports().stream()
                    .anyMatch(importDecl -> importDecl.getNameAsString().equals(OLD_TYPE));

            boolean hasReplacementTarget = cu.findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> "getJavaScriptResult".equals(call.getNameAsString()));

            if (!hasOldImport && !hasReplacementTarget && cu.findAll(ClassOrInterfaceType.class).stream()
                    .noneMatch(type -> matchesOldType(type.getNameAsString()))) {
                return;
            }

            LexicalPreservingPrinter.setup(cu);

            List<VariableDeclarationExpr> declarations = new ArrayList<>(cu.findAll(VariableDeclarationExpr.class));
            for (VariableDeclarationExpr declaration : declarations) {
                if (matchesOldType(declaration.getElementType()) && canRewriteDeclaration(declaration)) {
                    rewriteDeclaration(declaration);
                }
            }

            List<MethodCallExpr> calls = new ArrayList<>(cu.findAll(MethodCallExpr.class));
            for (MethodCallExpr call : calls) {
                rewriteJavaScriptResultCall(call);
            }

            cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(OLD_TYPE));

            Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static boolean canRewriteDeclaration(VariableDeclarationExpr declaration) {
        return declaration.getVariables().stream()
                .allMatch(variable -> variable.getInitializer().isPresent()
                        && isOldScriptResultCreation(variable.getInitializer().get()));
    }

    private static void rewriteDeclaration(VariableDeclarationExpr declaration) {
        declaration.setAllTypes(new TypeParser().objectType());
        for (VariableDeclarator variable : declaration.getVariables()) {
            variable.setInitializer(rewriteCreation(variable.getInitializer().get()));
        }
    }

    private static void rewriteJavaScriptResultCall(MethodCallExpr call) {
        if (!"getJavaScriptResult".equals(call.getNameAsString()) || call.getArguments().size() != 0) {
            return;
        }

        if (!call.getScope().isPresent()) {
            return;
        }

        call.replace(call.getScope().get().clone());
    }

    private static boolean isOldScriptResultCreation(Expression expression) {
        if (!(expression instanceof ObjectCreationExpr)) {
            return false;
        }

        ObjectCreationExpr creation = (ObjectCreationExpr) expression;
        return matchesOldType(creation.getType()) && creation.getArguments().size() == 1;
    }

    private static Expression rewriteCreation(Expression expression) {
        return ((ObjectCreationExpr) expression).getArgument(0).clone();
    }

    private static boolean matchesOldType(Type type) {
        return matchesOldType(type.asString());
    }

    private static boolean matchesOldType(String typeName) {
        return OLD_TYPE.equals(typeName) || OLD_SIMPLE_NAME.equals(typeName);
    }

    private static final class TypeParser {
        private final JavaParser parser = new JavaParser();

        private Type objectType() {
            return parser.parseType(NEW_TYPE).getResult().orElseThrow(IllegalStateException::new);
        }
    }
}
