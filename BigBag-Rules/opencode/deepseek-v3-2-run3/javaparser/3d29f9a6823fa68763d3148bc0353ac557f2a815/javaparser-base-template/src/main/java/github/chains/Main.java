package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Generic transformation to remove deprecated addEnabledLanguages method calls
    // Pattern: SomeClass.builder().addEnabledLanguages(...).otherMethods(...).build()
    // Becomes: SomeClass.builder().otherMethods(...).build()
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            processDirectory(sourcePath);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        List<Path> javaFiles = Files.walk(directory)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu != null) {
                    AddEnabledLanguagesRemover visitor = new AddEnabledLanguagesRemover();
                    Visitable result = cu.accept(visitor, null);
                    
                    if (visitor.isModified()) {
                        // Write back the modified file
                        Files.write(javaFile, cu.toString().getBytes());
                        System.out.println("Modified: " + javaFile);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * Generic visitor that removes .addEnabledLanguages(...) method calls
     * from builder method chains.
     * 
     * This transformation handles method chains where addEnabledLanguages appears.
     * The transformation is generic and works for any builder pattern that uses
     * the addEnabledLanguages method (not hardcoded to specific class names).
     */
    private static class AddEnabledLanguagesRemover extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // First visit children to handle nested structures
            super.visit(n, arg);
            
            // Check if this is a call to addEnabledLanguages
            if ("addEnabledLanguages".equals(n.getNameAsString())) {
                // Try to remove this method call from the chain
                if (removeMethodFromChain(n)) {
                    modified = true;
                    // Return the scope to replace addEnabledLanguages(...)
                    // This handles case where it's not in middle of chain
                    return n.getScope().orElse(null);
                }
            }
            
            return n;
        }
        
        /**
         * Remove a method call from a method chain by bypassing it.
         * Returns true if successful.
         */
        private boolean removeMethodFromChain(MethodCallExpr methodCall) {
            // Get the parent node
            Node parent = methodCall.getParentNode().orElse(null);
            
            if (parent instanceof MethodCallExpr) {
                MethodCallExpr parentCall = (MethodCallExpr) parent;
                
                // Check if methodCall is the scope of parentCall
                // This means we have: methodCall.someOtherMethod()
                if (parentCall.getScope().isPresent() && parentCall.getScope().get().equals(methodCall)) {
                    // Bypass methodCall: connect parentCall to methodCall's scope
                    if (methodCall.getScope().isPresent()) {
                        parentCall.setScope(methodCall.getScope().get());
                        return true;
                    }
                }
            }
            
            return false;
        }
    }
}