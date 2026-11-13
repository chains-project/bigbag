package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generic transformation to handle removal of a utility class with static constants.
 * 
 * This transformation handles the case where a class has been removed from a dependency
 * and its static field accesses need to be replaced with appropriate values.
 * 
 * The transformation is parameterized by:
 * 1. The fully-qualified class name that was removed
 * 2. A mapping from field names to replacement expressions
 * 3. The source directory to transform
 */
public class Main {
    
    /**
     * Configuration for the transformation.
     * Contains the class to remove and field mappings.
     */
    public static class TransformationConfig {
        private final String removedClassFqn;
        private final Map<String, String> fieldReplacements;
        private final String sourceDir;
        
        public TransformationConfig(String removedClassFqn, Map<String, String> fieldReplacements, String sourceDir) {
            this.removedClassFqn = removedClassFqn;
            this.fieldReplacements = fieldReplacements;
            this.sourceDir = sourceDir;
        }
        
        public String getRemovedClassFqn() {
            return removedClassFqn;
        }
        
        public Map<String, String> getFieldReplacements() {
            return fieldReplacements;
        }
        
        public String getSourceDir() {
            return sourceDir;
        }
    }
    
    /**
     * Visitor that removes imports of the specified class and replaces field accesses.
     */
    private static class StaticFieldReplacementVisitor extends ModifierVisitor<Void> {
        private final String removedClassSimpleName;
        private final String removedClassFqn;
        private final Map<String, String> fieldReplacements;
        private boolean modified = false;
        
        public StaticFieldReplacementVisitor(String removedClassFqn, Map<String, String> fieldReplacements) {
            this.removedClassFqn = removedClassFqn;
            this.removedClassSimpleName = removedClassFqn.substring(removedClassFqn.lastIndexOf('.') + 1);
            this.fieldReplacements = fieldReplacements;
        }
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            // Remove imports of the removed class
            if (id.getNameAsString().equals(removedClassFqn) || 
                id.getNameAsString().equals(removedClassSimpleName) ||
                id.getNameAsString().endsWith("." + removedClassSimpleName)) {
                modified = true;
                return null; // Remove this import
            }
            return (Node) super.visit(id, arg);
        }
        
        @Override
        public Node visit(FieldAccessExpr fa, Void arg) {
            // Check if this is a field access to the removed class
            if (fa.getScope().isNameExpr()) {
                NameExpr scope = fa.getScope().asNameExpr();
                String fieldName = fa.getNameAsString();
                
                // Check if this matches ClassName.FieldName pattern
                if (scope.getNameAsString().equals(removedClassSimpleName) && 
                    fieldReplacements.containsKey(fieldName)) {
                    
                    // Replace with the configured replacement
                    String replacement = fieldReplacements.get(fieldName);
                    modified = true;
                    
                    try {
                        // Try to parse as integer literal first (common case for constants)
                        int intValue = Integer.parseInt(replacement);
                        return new IntegerLiteralExpr(intValue);
                    } catch (NumberFormatException e) {
                        // Not an integer, create a name expression
                        try {
                            JavaParser parser = new JavaParser();
                            return parser.parseExpression(replacement).getResult().orElse(fa);
                        } catch (Exception ex) {
                            // If parsing fails, return the original
                            return fa;
                        }
                    }
                }
            }
            return (Node) super.visit(fa, arg);
        }
    }
    
    /**
     * Applies the transformation to all Java files in the source directory.
     */
    public static void applyTransformation(TransformationConfig config) throws IOException {
        String sourceDir = config.getSourceDir();
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                
                if (cu == null) {
                    continue;
                }
                
                StaticFieldReplacementVisitor visitor = new StaticFieldReplacementVisitor(
                    config.getRemovedClassFqn(), config.getFieldReplacements());
                
                cu.accept(visitor, null);
                
                if (visitor.wasModified()) {
                    modifiedFiles++;
                    // Write back the modified file
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("Modified: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Modified files: " + modifiedFiles + " of " + javaFiles.size());
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp . github.chains.Main <source-directory>");
            System.err.println("Example for jcabi-aspects Tv class removal:");
            System.err.println("  java -cp . github.chains.Main /path/to/project/src");
            return;
        }
        
        String sourceDir = args[0];
        
        // Configuration for jcabi-aspects Tv class removal
        // This mapping should be extracted from the old version of the library
        Map<String, String> tvFieldMappings = new HashMap<>();
        tvFieldMappings.put("THREE", "3");
        tvFieldMappings.put("FOUR", "4");
        tvFieldMappings.put("FIVE", "5");
        tvFieldMappings.put("SIX", "6");
        tvFieldMappings.put("TEN", "10");
        tvFieldMappings.put("FIFTEEN", "15");
        tvFieldMappings.put("TWENTY", "20");
        tvFieldMappings.put("THIRTY", "30");
        tvFieldMappings.put("HUNDRED", "100");
        tvFieldMappings.put("THOUSAND", "1000");
        tvFieldMappings.put("BILLION", "1000000000");
        // Add other constants as needed
        
        TransformationConfig config = new TransformationConfig(
            "com.jcabi.aspects.Tv",
            tvFieldMappings,
            sourceDir
        );
        
        try {
            applyTransformation(config);
        } catch (IOException e) {
            System.err.println("Error applying transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}