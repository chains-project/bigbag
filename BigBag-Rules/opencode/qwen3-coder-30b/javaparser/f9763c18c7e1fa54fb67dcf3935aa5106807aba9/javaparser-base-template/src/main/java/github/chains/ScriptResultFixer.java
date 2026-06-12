package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ScriptResultFixer {
    public static void main(String[] args) throws IOException {
        String sourceDirectory = "/workspace/code-coverage-api-plugin";
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        processDirectory(Paths.get(sourceDirectory));
        System.out.println("ScriptResult transformation completed.");
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(ScriptResultFixer::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to replace ScriptResult usage
            ScriptResultTransformationVisitor visitor = new ScriptResultTransformationVisitor();
            cu.accept(visitor, null);
            
            // Write back the modified file
            String result = cu.toString();
            Files.write(filePath, result.getBytes());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to transform ScriptResult usage in Java code
     */
    private static class ScriptResultTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Remove ScriptResult import
            if (n.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                return null; // Remove the import
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a ScriptResult constructor call
            if (n.getType().getNameAsString().equals("ScriptResult")) {
                // Replace with direct handling of result
                return replaceScriptResultConstructor(n);
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for calls to getJavaScriptResult method
            if (n.getScope().isPresent() && 
                n.getScope().get() instanceof NameExpr) {
                NameExpr scope = (NameExpr) n.getScope().get();
                // Replace calls to scriptResult.getJavaScriptResult() with just scriptResult
                if (n.getNameAsString().equals("getJavaScriptResult")) {
                    return scope; // Return just the scope (scriptResult)
                }
            }
            return super.visit(n, arg);
        }
        
        private Expression replaceScriptResultConstructor(ObjectCreationExpr n) {
            // Replace new ScriptResult(result) with just result
            NodeList<Expression> args = n.getArguments();
            if (args.size() == 1) {
                Expression resultExpr = args.get(0);
                // Return the expression directly instead of wrapping in ScriptResult
                return resultExpr;
            }
            return n;
        }
    }
}