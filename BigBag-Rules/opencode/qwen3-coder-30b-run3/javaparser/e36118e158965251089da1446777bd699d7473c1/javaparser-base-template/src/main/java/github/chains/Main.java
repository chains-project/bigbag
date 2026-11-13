package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix breaking API changes in HTTP libraries.
 * Specifically addresses breaking changes in ArtipieHttpException constructor signatures.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        String sourceDir = args.length > 0 ? args[0] : "/workspace/files-adapter/src";
        
        // Process all Java files in the source directory
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations to handle breaking API changes
            cu.accept(new HttpApiChangeVisitor(), null);
            
            // Save the transformed file
            Files.write(filePath, cu.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Failed to process " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to find and transform HTTP API breaking changes
     */
    private static class HttpApiChangeVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ConstructorCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Handle ArtipieHttpException constructor changes
            // Look for calls to ArtipieHttpException with 3 arguments where 2 are expected
            if (n.getType().asString().contains("ArtipieHttpException")) {
                handleArtipieHttpException(n);
            }
        }
        
        private void handleArtipieHttpException(ConstructorCallExpr expr) {
            NodeList<Expression> args = expr.getArguments();
            
            // Handle common breaking change: 3-arg constructor changed to 2-arg
            // Remove the third argument if it's likely a Throwable (common in older versions)
            if (args.size() >= 3) {
                // Check if third argument looks like a Throwable
                Expression thirdArg = args.get(2);
                
                // Simple heuristic: if argument is a name expression ending in "Throwable"
                if (thirdArg instanceof NameExpr) {
                    String argName = ((NameExpr) thirdArg).getNameAsString();
                    if (argName.toLowerCase().contains("throwable") || 
                        argName.toLowerCase().contains("exception")) {
                        // Create new argument list with 2 args instead of 3
                        NodeList<Expression> newArgs = new NodeList<>();
                        for (int i = 0; i < 2; i++) {
                            newArgs.add(args.get(i));
                        }
                        expr.setArguments(newArgs);
                    }
                }
                // If 3rd arg is not a name expression (like literal), remove it anyway
                else if (args.size() > 2) {
                    NodeList<Expression> newArgs = new NodeList<>();
                    for (int i = 0; i < 2; i++) {
                        newArgs.add(args.get(i));
                    }
                    expr.setArguments(newArgs);
                }
            }
        }
    }
}
