package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.Map;

/**
 * A generic Spoon transformation for fixing breaking changes where
 * a constants class has been removed from a dependency.
 * 
 * This transformation:
 * 1. Removes imports of the removed constants class
 * 2. Replaces static field accesses (e.g., Constants.CONSTANT_NAME) with literal values
 * 
 * The transformation is parameterized by:
 * - removedClassFqn: Fully qualified name of the removed constants class
 * - constantMappings: Map of constant names to their literal values
 */
public class Main {
    
    /**
     * Main method to run the transformation.
     * 
     * @param args Command line arguments:
     *             args[0]: Source directory to transform
     *             args[1]: Output directory for transformed code
     *             args[2]: Fully qualified name of removed constants class
     *             args[3+]: Pairs of constantName=literalValue (e.g., TEN=10 EIGHT=8)
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java Main <sourceDir> <outputDir> <removedClassFqn> [constantName=value ...]");
            System.err.println("Example: java Main ./src ./output com.jcabi.aspects.Tv TEN=10 EIGHT=8");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String removedClassFqn = args[2];
        
        // Parse constant mappings
        Map<String, Object> constantMappings = new HashMap<>();
        for (int i = 3; i < args.length; i++) {
            String[] parts = args[i].split("=", 2);
            if (parts.length == 2) {
                String constantName = parts[0];
                String value = parts[1];
                
                // Try to parse as integer, fall back to string
                try {
                    constantMappings.put(constantName, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    constantMappings.put(constantName, value);
                }
            }
        }
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Removed constants class: " + removedClassFqn);
        System.out.println("Constant mappings: " + constantMappings);
        
        try {
            transformProject(sourceDir, outputDir, removedClassFqn, constantMappings);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Apply the transformation to a project.
     */
    private static void transformProject(String sourceDir, String outputDir, 
                                        String removedClassFqn, 
                                        Map<String, Object> constantMappings) {
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Remove imports of the removed class
        removeImports(model, removedClassFqn);
        
        // Replace constant accesses with literals
        replaceConstantAccesses(model, removedClassFqn, constantMappings);
        
        // Write transformed code to output directory
        launcher.prettyprint();
    }
    
    /**
     * Remove all imports of the specified class.
     * Note: Spoon's import model might not include unresolved imports,
     * so imports may need to be removed manually from the source files.
     */
    private static void removeImports(CtModel model, String removedClassFqn) {
        // Try to remove imports via Spoon's model
        for (CtImport importDecl : model.getElements(new TypeFilter<CtImport>(CtImport.class))) {
            if (importDecl.getReference() != null) {
                String importFqn = importDecl.getReference().toString();
                if (importFqn.equals(removedClassFqn) || importFqn.startsWith(removedClassFqn + ".")) {
                    importDecl.delete();
                }
            }
        }
    }
    
    /**
     * Replace static field accesses of the removed constants class with literal values.
     */
    private static void replaceConstantAccesses(CtModel model, String removedClassFqn, 
                                               Map<String, Object> constantMappings) {
        // Find all field accesses
        model.getElements(new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class)).forEach(fieldAccess -> {
            // Try to get the field access as string and match pattern
            String fieldAccessStr = fieldAccess.toString();
            
            // Pattern: removedClassFqn.CONSTANT_NAME
            String pattern = removedClassFqn + ".";
            if (fieldAccessStr.startsWith(pattern)) {
                // Extract constant name after the last dot
                String constantName = fieldAccessStr.substring(pattern.length());
                
                // Check if we have a mapping for this constant
                if (constantMappings.containsKey(constantName)) {
                    Object value = constantMappings.get(constantName);
                    
                    // Create a literal to replace the field access
                    CtLiteral<?> literal = fieldAccess.getFactory().createLiteral(value);
                    
                    // Replace the field access with the literal
                    fieldAccess.replace(literal);
                }
            }
        });
    }
}