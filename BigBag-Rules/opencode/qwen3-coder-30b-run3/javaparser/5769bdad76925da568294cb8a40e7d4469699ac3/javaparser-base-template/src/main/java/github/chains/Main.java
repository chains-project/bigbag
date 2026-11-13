package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithExpression;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Generic JavaParser transformation to fix breaking changes in GitHub API
 * Specifically addresses the issue where GHCompare.Status.status became private
 * and requires using getStatus() method instead.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the input directory
        String inputDir = args.length > 0 ? args[0] : "/workspace/incrementals-tools";
        processDirectory(inputDir);
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        Files.walk(Paths.get(directoryPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Create a custom modifier visitor
            GHCompareStatusFixer visitor = new GHCompareStatusFixer();
            cu.accept(visitor, null);
            
            // Write the modified content back to file
            Files.write(filePath, cu.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes direct field access to GHCompare.Status.status 
     * by replacing it with getStatus() method calls.
     */
    private static class GHCompareStatusFixer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(FieldAccessExpr fieldAccessExpr, Void arg) {
            // Check if this is accessing status field and its scope is a method call returning GHCompare
            if (fieldAccessExpr.getNameAsString().equals("status")) {
                // Check if scope is a method call expression that returns GHCompare object
                if (fieldAccessExpr.getScope().isMethodCallExpr()) {
                    // If the field access is in the form: ...getCompare(...).status
                    // We want to replace it with: ...getCompare(...).getStatus()
                    MethodCallExpr scope = fieldAccessExpr.getScope().asMethodCallExpr();
                    // Replace the field access with a method call
                    MethodCallExpr newMethodCall = new MethodCallExpr(scope, "getStatus");
                    return newMethodCall;
                }
            }
            return super.visit(fieldAccessExpr, arg);
        }
    }
}
