package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic transformation rule for fixing SnakeYAML 2.0 breaking changes
 * 
 * Breaking change characterization:
 * - Old API pattern: new Yaml(constructor, representer, dumperOptions, resolver) 
 * - New API pattern: new Yaml(constructor, representer, dumperOptions)
 * - Structural transformation: Remove the fourth argument (resolver) which was removed in SnakeYAML 2.0
 */
public class Main {
    public static void main(String[] args) throws Exception {
        // For testing purposes
        System.out.println("Generic SnakeYAML 2.0 transformation rule");
    }

    /**
     * Apply the SnakeYAML 2.0 constructor fix to all Java files in the source directory
     */
    public static void transformSnakeYamlConstructors(String sourceDir) throws Exception {
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    // Parse the Java file
                    CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(path.toFile()));
                    
                    // Apply transformation
                    new YamlConstructorTransformer().visit(cu, null);
                    
                    // Save back to file
                    try (FileWriter writer = new FileWriter(path.toFile())) {
                        writer.write(cu.toString());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                }
            });
    }
    
    /**
     * Transformer that removes the fourth argument from SnakeYAML Yaml constructor calls
     * This fixes the breaking change introduced in SnakeYAML 2.0
     */
    private static class YamlConstructorTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Match SnakeYAML constructor calls that need to be updated
            if (n.getTypeAsString().equals("Yaml")) {
                // Check if it's the problematic four-parameter constructor (the one that had resolver as 4th param)
                if (n.getArguments().size() == 4) {
                    // Remove the fourth argument (resolver) which is no longer accepted in SnakeYAML 2.0
                    updateYamlConstructor(n);
                }
            }
        }
        
        /**
         * Updates the Yaml constructor call to remove the resolver parameter that was removed in SnakeYAML 2.0
         */
        private void updateYamlConstructor(ObjectCreationExpr n) {
            // The problematic constructor in older SnakeYAML:
            // new Yaml(constructor, representer, dumperOptions, resolver)
            //
            // The correct constructor in SnakeYAML 2.0:
            // new Yaml(constructor, representer, dumperOptions)
            //
            // Remove the fourth argument (resolver) which is no longer accepted
            if (n.getArguments().size() >= 4) {
                n.getArguments().remove(3);
            }
        }
    }
}
