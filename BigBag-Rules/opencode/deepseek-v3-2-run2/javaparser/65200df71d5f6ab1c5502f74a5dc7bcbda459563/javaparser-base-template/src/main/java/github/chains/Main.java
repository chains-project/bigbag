package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    /**
     * Generic transformation to remove calls to addEnabledLanguages method
     * from AnalysisEngineConfiguration.Builder instances.
     * 
     * This transformation addresses the breaking change where:
     * - Old API: AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)
     * - New API: Method removed - languages are configured differently
     * 
     * The transformation removes the problematic method call while preserving
     * the rest of the builder chain.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int removedCalls = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile);
                CompilationUnit cu = StaticJavaParser.parse(content);
                
                RemoveAddEnabledLanguagesVisitor visitor = new RemoveAddEnabledLanguagesVisitor();
                cu.accept(visitor, null);
                
                if (visitor.wasModified()) {
                    Files.writeString(javaFile, cu.toString());
                    transformedFiles++;
                    removedCalls += visitor.getRemovedCalls();
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("- Files transformed: " + transformedFiles);
        System.out.println("- addEnabledLanguages calls removed: " + removedCalls);
    }
    
    /**
     * Visitor that removes calls to addEnabledLanguages method from
     * AnalysisEngineConfiguration.Builder instances.
     */
    private static class RemoveAddEnabledLanguagesVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        private int removedCalls = 0;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to addEnabledLanguages
            if ("addEnabledLanguages".equals(n.getNameAsString())) {
                // Remove this method call from the chain
                // The parent should be another MethodCallExpr or the builder itself
                Node parent = n.getParentNode().orElse(null);
                
                if (parent instanceof MethodCallExpr) {
                    MethodCallExpr parentCall = (MethodCallExpr) parent;
                    // Replace the parent call with its scope (the previous call in chain)
                    // but preserve any subsequent method calls
                    Node grandParent = parentCall.getParentNode().orElse(null);
                    if (grandParent != null) {
                        // Get the scope of the current call (previous method in chain)
                        Expression scope = n.getScope().orElse(null);
                        if (scope != null) {
                            // Replace the parent call with the scope
                            parentCall.replace(scope);
                            modified = true;
                            removedCalls++;
                            return null; // Don't visit the removed node
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
        
        public int getRemovedCalls() {
            return removedCalls;
        }
    }
}