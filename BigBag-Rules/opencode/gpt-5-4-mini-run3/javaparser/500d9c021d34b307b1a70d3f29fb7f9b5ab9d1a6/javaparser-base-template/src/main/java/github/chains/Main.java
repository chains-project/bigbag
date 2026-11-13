package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
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

public class Main {

    private static final String OLD_PACKAGE = "javax.mvc";
    private static final String NEW_PACKAGE = "jakarta.mvc";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        JavaParser parser = new JavaParser(configuration);

        try {
            List<Path> javaFiles;
            try (var stream = Files.walk(sourceDir)) {
                javaFiles = stream
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());
            }

            for (Path file : javaFiles) {
                String original = Files.readString(file, StandardCharsets.UTF_8);
                if (!original.contains(OLD_PACKAGE)) {
                    continue;
                }

                CompilationUnit cu = parser.parse(original).getResult()
                        .orElseThrow(() -> new IllegalStateException("Failed to parse " + file));
                LexicalPreservingPrinter.setup(cu);

                rewriteCompilationUnit(cu);

                String rewritten = LexicalPreservingPrinter.print(cu);
                if (!rewritten.equals(original)) {
                    Files.writeString(file, rewritten, StandardCharsets.UTF_8);
                    System.out.println("Updated " + sourceDir.relativize(file));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to rewrite sources", ex);
        }
    }

    private static void rewriteCompilationUnit(CompilationUnit cu) {
        cu.getImports().forEach(Main::rewriteImport);
        cu.findAll(NameExpr.class).forEach(Main::rewriteNameExpr);
        cu.findAll(ClassOrInterfaceType.class).forEach(Main::rewriteTypeName);
        cu.findAll(ObjectCreationExpr.class).forEach(Main::rewriteObjectCreation);
        cu.findAll(AnnotationExpr.class).forEach(Main::rewriteAnnotation);
    }

    private static void rewriteImport(ImportDeclaration importDeclaration) {
        String name = importDeclaration.getNameAsString();
        if (name.startsWith(OLD_PACKAGE)) {
            importDeclaration.setName(name.replace(OLD_PACKAGE, NEW_PACKAGE));
        }
    }

    private static void rewriteNameExpr(NameExpr nameExpr) {
        String name = nameExpr.getNameAsString();
        if (name.startsWith(OLD_PACKAGE)) {
            nameExpr.setName(name.replace(OLD_PACKAGE, NEW_PACKAGE));
        }
    }

    private static void rewriteTypeName(ClassOrInterfaceType type) {
        type.getScope().ifPresent(scope -> {
            String scopeName = scope.asString();
            if (scopeName.startsWith(OLD_PACKAGE)) {
                type.setScope(parseType(scopeName.replace(OLD_PACKAGE, NEW_PACKAGE)));
            }
        });
    }

    private static void rewriteObjectCreation(ObjectCreationExpr expr) {
        expr.getType().getScope().ifPresent(scope -> {
            String scopeName = scope.asString();
            if (scopeName.startsWith(OLD_PACKAGE)) {
                expr.getType().setScope(parseType(scopeName.replace(OLD_PACKAGE, NEW_PACKAGE)));
            }
        });
    }

    private static void rewriteAnnotation(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        if (name.startsWith(OLD_PACKAGE)) {
            annotation.setName(name.replace(OLD_PACKAGE, NEW_PACKAGE));
        }
    }

    private static ClassOrInterfaceType parseType(String text) {
        return new JavaParser().parseClassOrInterfaceType(text)
                .getResult()
                .orElseThrow(() -> new IllegalStateException("Unable to parse type: " + text));
    }
}
