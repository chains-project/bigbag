package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the source directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Create a visitor to find and fix ScriptResult usage
            ScriptResultFixVisitor visitor = new ScriptResultFixVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            String output = LexicalPreservingPrinter.print(cu);
            Files.write(filePath, output.getBytes());
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ScriptResultFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            // Check if this is creating a ScriptResult instance from com.gargoylesoftware.htmlunit
            if (n.getType().asString().equals("ScriptResult")) {
                // Check if the constructor is from com.gargoylesoftware.htmlunit.ScriptResult
                if (n.getType().getScope().isPresent() && 
                    n.getType().getScope().get().asString().equals("com.gargoylesoftware.htmlunit")) {
                    
                    // Replace the object creation with direct usage of argument
                    // If there's an argument, we replace the entire expression with just the argument
                    if (n.getArguments().size() > 0) {
                        Expression argExpr = n.getArguments().get(0);
                        n.replace(argExpr);
                    } else {
                        // If no arguments, create a null expression to avoid compilation errors
                        NullLiteralExpr nullExpr = new NullLiteralExpr();
                        n.replace(nullExpr);
                    }
                }
            }
            
            super.visit(n, arg);
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            // Check for calls to getJavaScriptResult() on ScriptResult objects
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Check if the method is called on a ScriptResult object
                if (n.getScope().isPresent()) {
                    Expression scope = n.getScope().get();
                    
                    // If the scope is a ScriptResult expression, we can replace the method call
                    // with just the expression, since ScriptResult was being used to wrap the result
                    // and now we can directly use the result
                    n.replace(scope);
                }
            }
            
            super.visit(n, arg);
        }
    }
}