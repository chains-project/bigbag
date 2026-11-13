package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    
    // Configuration parameters - these should be changed for different breaking changes
    private static final String OLD_IMPORT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_IMPORT = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    private static final String OLD_CLASS_NAME = "ScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(content);
            
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse file " + filePath);
                return;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            boolean modified = false;
            
            // Check if file uses the old import
            boolean hasOldImport = cu.getImports().stream()
                .anyMatch(importDecl -> importDecl.getNameAsString().equals(OLD_IMPORT));
            
            if (hasOldImport) {
                System.out.println("Processing file: " + filePath);
                
                // Apply transformations
                ScriptResultTransformer transformer = new ScriptResultTransformer();
                cu.accept(transformer, null);
                
                // Replace imports
                cu.getImports().removeIf(importDecl -> 
                    importDecl.getNameAsString().equals(OLD_IMPORT));
                
                // Add new import if not already present
                boolean hasNewImport = cu.getImports().stream()
                    .anyMatch(importDecl -> importDecl.getNameAsString().equals(NEW_IMPORT));
                if (!hasNewImport) {
                    cu.addImport(NEW_IMPORT);
                }
                
                modified = true;
            }
            
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Updated file: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Generic transformer for ScriptResult API migration
     * This handles the structural transformation from old API pattern to new API pattern
     */
    private static class ScriptResultTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Transform ScriptResult constructor calls
            if (n.getType().asString().equals(OLD_CLASS_NAME)) {
                NodeList<Expression> arguments = n.getArguments();
                if (!arguments.isEmpty()) {
                    // Old constructor: ScriptResult(Object result)
                    // New constructor: ScriptResult(String result)
                    // We need to convert Object to String if necessary
                    Expression firstArg = arguments.get(0);
                    
                    // If the argument is not already a String, wrap it with toString()
                    if (!isStringExpression(firstArg)) {
                        MethodCallExpr toStringCall = new MethodCallExpr(firstArg.clone(), "toString");
                        n.setArgument(0, toStringCall);
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Transform getJavaScriptResult() method calls
            if (n.getNameAsString().equals("getJavaScriptResult")) {
                // Old: scriptResult.getJavaScriptResult() returns Object
                // New: ScriptResult doesn't have this method - need to access result directly
                // Since we can't know the exact replacement, we'll replace with toString()
                // This is a safe fallback since the result should be convertible to String
                n.setName("toString");
            }
            return super.visit(n, arg);
        }
        
        private boolean isStringExpression(Expression expr) {
            String exprStr = expr.toString();
            // Check if it's a string literal
            if (exprStr.startsWith("\"") && exprStr.endsWith("\"")) {
                return true;
            }
            // Check if it's a method call that returns String
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                String methodName = methodCall.getNameAsString();
                // Common String-returning methods
                return methodName.equals("toString") || 
                       methodName.equals("getText") || 
                       methodName.equals("getValue") ||
                       methodName.equals("asText");
            }
            return false;
        }
    }
}