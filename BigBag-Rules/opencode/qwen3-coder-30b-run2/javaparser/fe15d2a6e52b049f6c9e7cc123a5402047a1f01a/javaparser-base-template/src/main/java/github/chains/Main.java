package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        processDirectory(new File(sourceDir));
    }

    private static void processDirectory(File dir) throws IOException {
        if (!dir.exists()) {
            throw new IOException("Directory does not exist: " + dir.getAbsolutePath());
        }

        Files.walk(dir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(content);

            // Apply transformations
            new StrategyMethodCallFixer().visit(cu, null);

            // Print modified content (simple approach)
            String modifiedContent = cu.toString();
            
            // Write back to file
            Files.write(filePath, modifiedContent.getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Visitor that fixes calls to getInstance() methods on JAXB strategy classes
     * to use the INSTANCE field instead
     */
    private static class StrategyMethodCallFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a call to getInstance() on a strategy class
            if (isGetInstanceCall(methodCall)) {
                // Replace with direct field access to INSTANCE
                replaceGetInstanceWithInstance(methodCall);
            }
        }
        
        private boolean isGetInstanceCall(MethodCallExpr methodCall) {
            // Check if it's a method call to getInstance()
            if (methodCall.getNameAsString().equals("getInstance")) {
                // Get the expression being called on
                if (methodCall.getScope().isPresent()) {
                    var scope = methodCall.getScope().get();
                    
                    // Check if it's a field access expression with known strategy classes
                    if (scope.isFieldAccessExpr()) {
                        var fieldAccess = scope.asFieldAccessExpr();
                        var fieldName = fieldAccess.getNameAsString();
                        
                        // Check if we're dealing with a JAXB strategy class
                        return isJAXBStrategyClass(fieldName);
                    }
                    // Handle cases where the class name is directly referenced
                    else if (scope.isNameExpr()) {
                        var nameExpr = scope.asNameExpr();
                        var className = nameExpr.getNameAsString();
                        return isJAXBStrategyClass(className);
                    }
                }
            }
            return false;
        }
        
        private boolean isJAXBStrategyClass(String className) {
            Set<String> strategyClasses = Set.of(
                "JAXBToStringStrategy",
                "JAXBEqualsStrategy", 
                "JAXBHashCodeStrategy",
                "JAXBCopyStrategy",
                "JAXBMergeStrategy"
            );
            
            return strategyClasses.contains(className);
        }
        
        private void replaceGetInstanceWithInstance(MethodCallExpr methodCall) {
            // Get the scope (the class or object calling getInstance)
            if (methodCall.getScope().isPresent()) {
                var scope = methodCall.getScope().get();
                
                // Create a field access expression to INSTANCE
                FieldAccessExpr newExpression = new FieldAccessExpr(
                    scope, 
                    "INSTANCE"
                );
                
                // Replace the entire method call with the field access
                methodCall.replace(newExpression);
            }
        }
    }
}