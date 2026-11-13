package io.qameta.allure.maven;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Generic JavaParser transformation to fix zip4j breaking changes.
 * This tool identifies and fixes imports from the removed net.lingala.zip4j.core package.
 * 
 * Usage: java -jar zip4j-breaking-change-fixer-1.0.0.jar /path/to/project
 */
public class Zip4jBreakingChangeFixer {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java -jar zip4j-breaking-change-fixer-1.0.0.jar /path/to/project");
            System.exit(1);
        }

        Path projectPath = Paths.get(args[0]);
        if (!Files.exists(projectPath)) {
            System.err.println("Project path does not exist: " + projectPath);
            System.exit(1);
        }

        System.out.println("Fixing zip4j breaking changes in: " + projectPath);
        fixProject(projectPath);
        System.out.println("Fixing completed.");
    }

    private static void fixProject(Path projectPath) throws IOException {
        Files.walkFileTree(projectPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    fixJavaFile(file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void fixJavaFile(Path filePath) throws IOException {
        String code = Files.readString(filePath);
        
        // Parse the Java file
        JavaParser parser = new JavaParser();
        try {
            CompilationUnit cu = parser.parse(code).getResult().orElse(null);
            if (cu == null) {
                return;
            }
            
            boolean needsFix = false;
            
            // Check for imports that need to be fixed
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.isStatic()) continue;
                String importName = importDecl.getName().asString();
                if (importName.startsWith("net.lingala.zip4j.core.")) {
                    // Replace net.lingala.zip4j.core.* with net.lingala.zip4j.*
                    String fixedImport = importName.replace("net.lingala.zip4j.core.", "net.lingala.zip4j.");
                    importDecl.setName(fixedImport);
                    System.out.println("Fixed import in " + filePath + ": " + importName + " -> " + fixedImport);
                    needsFix = true;
                }
            }
            
            // If changes were made, write back the fixed code
            if (needsFix) {
                String fixedCode = LexicalPreservingPrinter.print(cu);
                Files.write(filePath, fixedCode.getBytes());
                System.out.println("Updated file: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}
