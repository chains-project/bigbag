package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Main {

    private static final String OLD_ZIPFILE_FQN = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_ZIPFILE_FQN = "net.lingala.zip4j.ZipFile";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRootPath = Paths.get(args[0]);
        final SourceRoot sourceRoot = new SourceRoot(sourceRootPath);
        sourceRoot.tryToParse();

        for (CompilationUnit compilationUnit : sourceRoot.getCompilationUnits()) {
            rewriteCompilationUnit(compilationUnit);
        }

        sourceRoot.saveAll();
    }

    private static void rewriteCompilationUnit(final CompilationUnit compilationUnit) {
        final boolean removedOldImport = compilationUnit.getImports().removeIf(
                importDeclaration -> OLD_ZIPFILE_FQN.equals(importDeclaration.getNameAsString()));

        if (removedOldImport
                && compilationUnit.getImports().stream()
                .noneMatch(importDeclaration -> NEW_ZIPFILE_FQN.equals(importDeclaration.getNameAsString()))) {
            compilationUnit.addImport(NEW_ZIPFILE_FQN);
        }

        compilationUnit.findAll(ClassOrInterfaceType.class).forEach(type -> {
            if (OLD_ZIPFILE_FQN.equals(type.asString())) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_ZIPFILE_FQN));
            }
        });

        compilationUnit.findAll(ObjectCreationExpr.class).forEach(objectCreationExpr -> {
            if (objectCreationExpr.getType().asString().equals(OLD_ZIPFILE_FQN)) {
                objectCreationExpr.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_ZIPFILE_FQN));
            }
        });
    }
}
