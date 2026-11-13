package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Main {

    private static final String OLD_TYPE = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_TYPE = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a source directory: " + root);
        }

        AtomicInteger changedFiles = new AtomicInteger();
        AtomicInteger changedOccurrences = new AtomicInteger();

        try {
            List<Path> javaFiles = Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                CompilationUnit cu = StaticJavaParser.parse(source);
                LexicalPreservingPrinter.setup(cu);

                int before = countOccurrences(cu);
                if (before == 0) {
                    continue;
                }

                rewrite(cu);
                int after = countOccurrences(cu);
                if (after != 0) {
                    throw new IllegalStateException("Rewrite left old references in " + file + ": " + after);
                }

                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                changedFiles.incrementAndGet();
                changedOccurrences.addAndGet(before);
            }

            System.out.println("Updated " + changedOccurrences.get() + " occurrence(s) across " + changedFiles.get() + " file(s).");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewrite(CompilationUnit cu) {
        for (ImportDeclaration importDeclaration : cu.findAll(ImportDeclaration.class)) {
            if (OLD_TYPE.equals(importDeclaration.getNameAsString())) {
                importDeclaration.setName(NEW_TYPE);
            }
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (OLD_TYPE.equals(type.toString())) {
                type.replace(StaticJavaParser.parseType(NEW_TYPE));
            }
        }

        for (ClassExpr classExpr : cu.findAll(ClassExpr.class)) {
            if (OLD_TYPE.equals(classExpr.getType().toString())) {
                classExpr.setType(NEW_TYPE);
            }
        }

        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            if (OLD_TYPE.equals(creationExpr.getType().toString())) {
                creationExpr.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
            }
        }

        for (FieldAccessExpr fieldAccessExpr : cu.findAll(FieldAccessExpr.class)) {
            if (OLD_TYPE.equals(fieldAccessExpr.toString())) {
                fieldAccessExpr.replace(StaticJavaParser.parseExpression(NEW_TYPE));
            }
        }

        for (NameExpr nameExpr : cu.findAll(NameExpr.class)) {
            if (OLD_TYPE.equals(nameExpr.toString())) {
                nameExpr.replace(StaticJavaParser.parseExpression(NEW_TYPE));
            }
        }
    }

    private static int countOccurrences(CompilationUnit cu) {
        int count = 0;
        for (ImportDeclaration importDeclaration : cu.findAll(ImportDeclaration.class)) {
            if (OLD_TYPE.equals(importDeclaration.getNameAsString())) {
                count++;
            }
        }
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (OLD_TYPE.equals(type.toString())) {
                count++;
            }
        }
        for (ClassExpr classExpr : cu.findAll(ClassExpr.class)) {
            if (OLD_TYPE.equals(classExpr.getType().toString())) {
                count++;
            }
        }
        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            if (OLD_TYPE.equals(creationExpr.getType().toString())) {
                count++;
            }
        }
        for (FieldAccessExpr fieldAccessExpr : cu.findAll(FieldAccessExpr.class)) {
            if (OLD_TYPE.equals(fieldAccessExpr.toString())) {
                count++;
            }
        }
        for (NameExpr nameExpr : cu.findAll(NameExpr.class)) {
            if (OLD_TYPE.equals(nameExpr.toString())) {
                count++;
            }
        }
        return count;
    }
}
