package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        // Generic transformation to fix the TestListResolver.getWildcard() method call issue
        if (args.length == 0) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        try {
            // Process all Java files in the directory recursively
            Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            FileInputStream in = new FileInputStream(filePath.toString());
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            // Find all method calls to getWildcard() - the problematic pattern
            List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
            boolean modified = false;
            
            for (MethodCallExpr methodCall : methodCalls) {
                // Check if this is a call to getWildcard() method that needs to be replaced
                if (methodCall.getNameAsString().equals("getWildcard")) {
                    // Replace with just the string literal "*" to solve the compilation error
                    // The getWildcard() method was removed from Maven Surefire in newer versions
                    methodCall.setArguments(new com.github.javaparser.ast.NodeList<>(new StringLiteralExpr("*")));
                    modified = true;
                }
            }
            
            if (modified) {
                // Write the modified code back to the file
                try (FileWriter writer = new FileWriter(filePath.toString())) {
                    writer.write(LexicalPreservingPrinter.print(cu));
                }
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}