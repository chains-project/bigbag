package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

public class Main {
    private static final String HTMLUNIT_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: the source directory to rewrite");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(final Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = rewriteScriptResultDeclarations(compilationUnit);
            changed |= rewriteJavaScriptResultAccess(compilationUnit);
            changed |= removeHtmlUnitImport(compilationUnit);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to rewrite " + file, exception);
        }
    }

    private static boolean rewriteScriptResultDeclarations(final CompilationUnit compilationUnit) {
        boolean changed = false;

        for (VariableDeclarator declarator : compilationUnit.findAll(VariableDeclarator.class)) {
            if (isHtmlUnitScriptResultType(declarator.getType()) && declarator.getInitializer().isPresent()) {
                Optional<Expression> replacement = unwrapScriptResultCreation(declarator.getInitializer().get());
                if (replacement.isPresent()) {
                    declarator.setType(new ClassOrInterfaceType(null, "Object"));
                    declarator.setInitializer(replacement.get().clone());
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean rewriteJavaScriptResultAccess(final CompilationUnit compilationUnit) {
        boolean changed = false;

        for (MethodCallExpr methodCall : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!"getJavaScriptResult".equals(methodCall.getNameAsString()) || methodCall.getScope().isEmpty()) {
                continue;
            }

            Optional<Expression> replacement = unwrapScriptResultCreation(methodCall.getScope().get());
            if (replacement.isPresent()) {
                methodCall.replace(replacement.get().clone());
            }
            else {
                methodCall.replace(methodCall.getScope().get().clone());
            }
            changed = true;
        }

        return changed;
    }

    private static boolean removeHtmlUnitImport(final CompilationUnit compilationUnit) {
        return compilationUnit.getImports().removeIf(importDeclaration ->
                HTMLUNIT_SCRIPT_RESULT.equals(importDeclaration.getNameAsString()));
    }

    private static boolean isHtmlUnitScriptResultType(final ClassOrInterfaceType type) {
        return "ScriptResult".equals(type.getNameAsString()) || HTMLUNIT_SCRIPT_RESULT.equals(type.asString());
    }

    private static Optional<Expression> unwrapScriptResultCreation(final Expression expression) {
        if (expression.isObjectCreationExpr()) {
            ObjectCreationExpr creation = expression.asObjectCreationExpr();
            if (isHtmlUnitScriptResultType(creation.getType()) && !creation.getArguments().isEmpty()) {
                return Optional.of(creation.getArgument(0));
            }
        }
        return Optional.empty();
    }

    private static boolean isHtmlUnitScriptResultType(final com.github.javaparser.ast.type.Type type) {
        return type.isClassOrInterfaceType() && isHtmlUnitScriptResultType(type.asClassOrInterfaceType());
    }
}
