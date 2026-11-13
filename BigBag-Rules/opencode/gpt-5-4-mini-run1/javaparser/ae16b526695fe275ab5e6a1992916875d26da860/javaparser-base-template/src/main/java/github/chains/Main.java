package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_TYPE = "org.eclipse.jetty.server.nio.NetworkTrafficSelectChannelConnector";
    private static final String OLD_SIMPLE_NAME = "SelectChannelConnector";
    private static final String NEW_SIMPLE_NAME = "NetworkTrafficSelectChannelConnector";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = rewriteCompilationUnit(compilationUnit);
            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process " + path, e);
        }
    }

    private static boolean rewriteCompilationUnit(CompilationUnit compilationUnit) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.findAll(ImportDeclaration.class)) {
            if (importDeclaration.getNameAsString().equals(OLD_TYPE)) {
                importDeclaration.setName(NEW_TYPE);
                changed = true;
            }
        }

        for (ObjectCreationExpr objectCreationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            String typeName = objectCreationExpr.getType().asString();
            if (typeName.equals(OLD_SIMPLE_NAME) || typeName.equals(OLD_TYPE)) {
                objectCreationExpr.setType(NEW_SIMPLE_NAME);
                ensureImport(compilationUnit);
                changed = true;
            }
        }

        for (ClassOrInterfaceType classOrInterfaceType : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            String typeName = classOrInterfaceType.asString();
            if (typeName.equals(OLD_SIMPLE_NAME) || typeName.equals(OLD_TYPE)) {
                classOrInterfaceType.setName(NEW_SIMPLE_NAME);
                classOrInterfaceType.removeScope();
                ensureImport(compilationUnit);
                changed = true;
            }
        }

        return changed;
    }

    private static void ensureImport(CompilationUnit compilationUnit) {
        if (compilationUnit.getImports().stream().noneMatch(importDeclaration -> importDeclaration.getNameAsString().equals(NEW_TYPE))) {
            compilationUnit.addImport(NEW_TYPE);
        }
    }
}
