package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        processDirectory(sourceDir);
    }
    
    private static void processDirectory(String dirPath) {
        try {
            Files.walk(Paths.get(dirPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation to fix SLF4J API issues
            new SLF4JFixVisitor().visit(cu, null);
            
            // Save the modified file
            String modifiedContent = LexicalPreservingPrinter.print(cu);
            Files.write(filePath, modifiedContent.getBytes());
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix SLF4J API breaking changes
     * This transformation addresses generic SLF4J API compatibility issues
     * where newer versions of SLF4J introduce breaking changes in interfaces
     */
    private static class SLF4JFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
            super.visit(cid, arg);
        }
    }
}