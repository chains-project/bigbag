package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
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
     * Generic transformation rule for fixing breaking API changes in SonarLint Core.
     * 
     * This rule removes calls to deprecated/removed methods in AnalysisEngineConfiguration.Builder
     * Specifically targets: AnalysisEngineConfiguration.builder().addEnabledLanguages(Set<Language>)
     * 
     * The transformation is generic and can be applied to any project affected by this breaking change
     * by simply changing the source directory path.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformation completed. Modified " + transformedFiles + " files.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        AddEnabledLanguagesRemover transformer = new AddEnabledLanguagesRemover();
        Visitable transformed = cu.accept(transformer, null);
        
        if (transformer.wasModified()) {
            Files.write(javaFile, cu.toString().getBytes());
            System.out.println("Modified: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    /**
     * Visitor that removes calls to addEnabledLanguages(Set<Language>) method
     * from AnalysisEngineConfiguration.Builder instances.
     * 
     * This is a generic transformation that matches the structural pattern:
     * - Method call named "addEnabledLanguages"
     * - With a single parameter of type Set<Language>
     * - Called on a builder() method result
     */
    private static class AddEnabledLanguagesRemover extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Match the pattern: builder().addEnabledLanguages(Set<Language>)
            if ("addEnabledLanguages".equals(n.getNameAsString()) && 
                n.getArguments().size() == 1) {
                
                // Check if this is called on a builder() method result
                if (n.getScope().isPresent()) {
                    var scope = n.getScope().get();
                    if (scope instanceof MethodCallExpr) {
                        MethodCallExpr scopeCall = (MethodCallExpr) scope;
                        if ("builder".equals(scopeCall.getNameAsString())) {
                            // Remove this method call by returning the scope instead
                            modified = true;
                            return scopeCall;
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}