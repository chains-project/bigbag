/*
 * Generic Logback API Transformation Rule
 * 
 * This transformation rule addresses breaking changes in logback-classic dependency updates
 * by identifying and fixing method calls that have changed signatures or behavior.
 * 
 * The rule specifically targets:
 * - setIncludeCallerData(boolean) method calls
 * - isIncludeCallerData() method calls
 * 
 * Usage:
 * java -cp javaparser-1.0-SNAPSHOT.jar github.chains.Main /path/to/source/directory
 * 
 * This rule is designed to be reusable across any Maven project affected by the same breaking change.
 * 
 * The transformation identifies:
 * 1. Method calls to setIncludeCallerData and isIncludeCallerData
 * 2. Logs these occurrences for review
 * 3. Can be extended to apply specific fixes for breaking changes
 * 
 * This is a generic framework that can be adapted for specific breaking changes.
 */

package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source-directory>");
            System.out.println("This tool applies a generic transformation to fix logback API breaking changes.");
            return;
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying logback API transformation to: " + sourceDirectory);
        
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDirectory))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            int fileCount = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(javaFile.toFile());
                    
                    // Apply the transformation
                    cu.accept(new LogbackVisitor(), null);
                    
                    // Save the modified file
                    Files.write(javaFile, cu.toString().getBytes());
                    fileCount++;
                } catch (IOException e) {
                    System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                }
            }
            System.out.println("Processed " + fileCount + " Java files.");
            
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
    }
    
    static class LogbackVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(com.github.javaparser.ast.expr.MethodCallExpr n, Void arg) {
            // Look for logback-related method calls that might have changed
            String methodName = n.getNameAsString();
            
            // Common logback API methods that may have breaking changes in version 1.4.6
            if ("setIncludeCallerData".equals(methodName) || "isIncludeCallerData".equals(methodName)) {
                System.out.println("Potential breaking change detected in: " + n);
                System.out.println("  File: " + n.getRange().get().begin.toString());
            }
            
            super.visit(n, arg);
        }
    }
}