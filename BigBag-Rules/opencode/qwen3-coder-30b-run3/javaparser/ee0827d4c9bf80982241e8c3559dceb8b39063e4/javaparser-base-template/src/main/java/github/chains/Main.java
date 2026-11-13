package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {
            // Process all Java files in the target directory
            if (args.length < 1) {
                System.out.println("Usage: java Main <source_directory>");
                return;
            }
            
            String sourceDirectory = args[0];
            processDirectory(new File(sourceDirectory));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File directory) throws IOException {
        if (directory.isFile() && directory.getName().endsWith(".java")) {
            processFile(directory);
        } else if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().equals(".DS_Store")) {
                        processDirectory(file);
                    }
                }
            }
        }
    }
    
    private static void processFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply transformation to fix commons-io API breaks
        new CommonsIOBreakageFixer().visit(cu, null);
        
        // Print the modified file
        String modifiedContent = LexicalPreservingPrinter.print(cu);
        Files.write(Paths.get(file.getAbsolutePath()), modifiedContent.getBytes());
    }
    
    private static class CommonsIOBreakageFixer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            String importedType = importDecl.getNameAsString();
            
            // Remove imports for classes that were removed
            if (importedType.equals("org.apache.commons.io.input.BoundedInputStream") ||
                importedType.equals("org.apache.commons.io.input.ClosedInputStream") ||
                importedType.equals("org.apache.commons.io.output.ThresholdingOutputStream") ||
                importedType.equals("org.apache.commons.io.output.NullPrintStream")) {
                importDecl.remove(); // Remove the import
            }
        }
    }
}