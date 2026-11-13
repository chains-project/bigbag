package github.chains;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.utils.SourceRoot;

public class Main {
    private static final String REMOVED_CLASS = "ScriptResult";
    private static final String REMOVED_FQCN = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try {
            new Main().transform(sourceRoot);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void transform(Path sourceRootPath) throws IOException {
        SourceRoot sourceRoot = new SourceRoot(sourceRootPath);
        List<com.github.javaparser.ParseResult<CompilationUnit>> results = sourceRoot.tryToParse();

        for (com.github.javaparser.ParseResult<CompilationUnit> result : results) {
            result.getResult().ifPresent(this::rewriteCompilationUnit);
        }

        sourceRoot.saveAll();
    }

    private void rewriteCompilationUnit(CompilationUnit cu) {
        removeOldImport(cu);

        cu.findAll(MethodCallExpr.class).forEach(this::rewriteGetJavaScriptResultCall);
        cu.findAll(ObjectCreationExpr.class).forEach(this::rewriteScriptResultConstruction);
        cu.findAll(ClassOrInterfaceType.class).forEach(this::rewriteScriptResultType);
    }

    private void removeOldImport(CompilationUnit cu) {
        cu.getImports().stream()
                .filter(importDeclaration -> !importDeclaration.isAsterisk()
                        && REMOVED_FQCN.equals(importDeclaration.getNameAsString()))
                .findFirst()
                .ifPresent(cu::remove);
    }

    private void rewriteGetJavaScriptResultCall(MethodCallExpr call) {
        if (!"getJavaScriptResult".equals(call.getNameAsString()) || call.getScope().isEmpty()) {
            return;
        }

        call.replace(call.getScope().get());
    }

    private void rewriteScriptResultConstruction(ObjectCreationExpr creation) {
        if (!isScriptResultType(creation.getType()) || creation.getArguments().isEmpty()) {
            return;
        }

        Expression replacement = creation.getArgument(0).clone();
        creation.replace(replacement);
    }

    private void rewriteScriptResultType(ClassOrInterfaceType type) {
        if (isScriptResultType(type)) {
            type.setName("Object");
        }
    }

    private boolean isScriptResultType(ClassOrInterfaceType type) {
        return REMOVED_CLASS.equals(type.getNameAsString());
    }
}
