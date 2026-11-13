package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_RENDERING_CONTEXT =
            "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_RENDERING_CONTEXT =
            "org.apache.maven.doxia.siterenderer.RenderingContext";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteJavaFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteJavaFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteQualifiedTypeUsages(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (importDeclaration.getNameAsString().equals(OLD_RENDERING_CONTEXT)) {
                importDeclaration.setName(NEW_RENDERING_CONTEXT);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedTypeUsages(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (matchesOldQualifiedType(type)) {
                replaceNode(type, NEW_RENDERING_CONTEXT);
                changed = true;
            }
        }

        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            if (matchesOldQualifiedType(creationExpr.getType())) {
                replaceNode(creationExpr.getType(), NEW_RENDERING_CONTEXT);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean matchesOldQualifiedType(Node node) {
        return Objects.equals(node.toString(), OLD_RENDERING_CONTEXT)
                || node.toString().endsWith("." + OLD_RENDERING_CONTEXT.substring(OLD_RENDERING_CONTEXT.lastIndexOf('.') + 1));
    }

    private static void replaceNode(Node target, String fullyQualifiedTypeName) {
        target.replace(StaticJavaParser.parseClassOrInterfaceType(fullyQualifiedTypeName));
    }
}
