package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SIMPLE_NAME = "ScriptResult";
    private static final String NEW_TYPE = "Object";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one source directory argument");
        }

        try {
            transform(Paths.get(args[0]));
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void transform(Path sourceRoot) throws IOException {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> javaFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                rewrite(file);
            }
        }
    }

    private static void rewrite(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        changed |= removeOldImport(cu);
        changed |= replaceScriptResultCreations(cu);
        changed |= replaceJavaScriptResultAccess(cu);
        changed |= replaceTypeReferences(cu);

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean removeOldImport(CompilationUnit cu) {
        return cu.getImports().removeIf(importDeclaration ->
                OLD_TYPE.equals(importDeclaration.getNameAsString()));
    }

    private static boolean replaceTypeReferences(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (isOldScriptResultType(type)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceScriptResultCreations(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (isOldScriptResultCreation(creation)) {
                creation.replace(creation.getArgument(0).clone());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceJavaScriptResultAccess(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("getJavaScriptResult".equals(call.getNameAsString())
                    && call.getScope().isPresent()
                    && call.getScope().get() instanceof ObjectCreationExpr) {
                ObjectCreationExpr creation = (ObjectCreationExpr) call.getScope().get();
                if (isOldScriptResultCreation(creation)) {
                    call.replace(creation.getArgument(0).clone());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean isOldScriptResultCreation(ObjectCreationExpr creation) {
        return isOldScriptResultType(creation.getType()) && creation.getArguments().size() == 1;
    }

    private static boolean isOldScriptResultType(ClassOrInterfaceType type) {
        String name = type.asString();
        return OLD_TYPE.equals(name) || OLD_SIMPLE_NAME.equals(type.getNameAsString());
    }
}
