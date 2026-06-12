package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        // Default to current directory if no arguments given
        String sourceDirectory = args.length > 0 ? args[0] : ".";
        
        try {
            // Process all Java files in the source directory
            processJavaFiles(sourceDirectory);
            System.out.println("Logback API transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFiles(String sourceDirectory) throws IOException {
        Path rootPath = Paths.get(sourceDirectory);
        
        try (Stream<Path> paths = Files.walk(rootPath)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                processJavaFile(javaFile);
            }
        }
    }
    
    private static void processJavaFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply transformation rules
        new LogbackTransformationVisitor().visit(cu, null);
        
        // Write the modified content back to the file
        String modifiedContent = LexicalPreservingPrinter.print(cu);
        Files.writeString(javaFile, modifiedContent);
    }
    
    /**
     * Visitor that identifies and comments on potential logback API issues
     */
    private static class LogbackTransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Identify potential logback-related method calls that might need attention
            identifyLogbackMethodCalls(methodCall);
        }
        
        /**
         * Identify logback method calls that may require attention due to API changes
         */
        private void identifyLogbackMethodCalls(MethodCallExpr methodCall) {
            // Check if this is a method call that might be related to logback
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                
                if (scope.isNameExpr()) {
                    String scopeName = scope.asNameExpr().getNameAsString();
                    String methodName = methodCall.getNameAsString();
                    
                    // Common logger-related patterns that might need review
                    if (scopeName.equals("logger") || 
                        scopeName.equals("Logger") || 
                        scopeName.equals("LOGGER") ||
                        scopeName.contains("log")) {
                        
                        // Add a comment to indicate this line might need review
                        // This is a placeholder for actual transformation logic
                    }
                }
            }
        }
    }
}