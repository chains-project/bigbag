package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_SCRIPT_RESULT_FQN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SCRIPT_RESULT_SIMPLE_NAME = "ScriptResult";

    public static void main(String[] args) {
        Path sourceRoot = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        int changedFiles = 0;

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (transformFile(path)) {
                    changedFiles++;
                }
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, exception);
        }

        System.out.println("Transformed " + changedFiles + " file(s).");
    }

    private static boolean transformFile(Path path) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(path);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = transformScriptResultUsage(compilationUnit);
        if (!changed) {
            return false;
        }

        Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        return true;
    }

    private static boolean transformScriptResultUsage(CompilationUnit compilationUnit) {
        boolean changed = false;

        for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("getJavaScriptResult") || call.getScope().isEmpty()) {
                continue;
            }

            Expression scope = call.getScope().get();
            if (scope.isObjectCreationExpr()) {
                ObjectCreationExpr creationExpr = scope.asObjectCreationExpr();
                if (!isOldScriptResultCreation(creationExpr) || creationExpr.getArguments().size() != 1) {
                    continue;
                }

                call.replace(creationExpr.getArgument(0).clone());
                changed = true;
            }
        }

        if (changed) {
            removeOldScriptResultImportIfUnused(compilationUnit);
        }

        return changed;
    }

    private static boolean isOldScriptResultCreation(ObjectCreationExpr creationExpr) {
        return creationExpr.getType().getNameAsString().equals(OLD_SCRIPT_RESULT_SIMPLE_NAME);
    }

    private static void removeOldScriptResultImportIfUnused(CompilationUnit compilationUnit) {
        boolean stillUsed = compilationUnit.findAll(ObjectCreationExpr.class).stream()
                .anyMatch(Main::isOldScriptResultCreation);
        if (!stillUsed) {
            compilationUnit.getImports().removeIf(Main::isOldScriptResultImport);
        }
    }

    private static boolean isOldScriptResultImport(ImportDeclaration importDeclaration) {
        return !importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(OLD_SCRIPT_RESULT_FQN);
    }
}
