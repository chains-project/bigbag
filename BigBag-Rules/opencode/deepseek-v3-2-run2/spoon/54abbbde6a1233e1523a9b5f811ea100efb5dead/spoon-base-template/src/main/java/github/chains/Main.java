package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic transformation rule for replacing static field accesses to a removed class
 * with literal values. This is designed to fix breaking changes where a constants
 * class is removed from a dependency.
 * 
 * This specific implementation handles the removal of com.jcabi.aspects.Tv class
 * from jcabi-aspects 0.25.1, replacing constants like Tv.SEVEN, Tv.TEN, Tv.MILLION
 * with their corresponding integer literals.
 * 
 * The transformation is generic in structure and can be adapted for similar
 * breaking changes by modifying the CONSTANT_MAPPINGS and TARGET_CLASS.
 */
public class Main {
    // Target class that was removed
    private static final String TARGET_CLASS = "com.jcabi.aspects.Tv";
    
    // Mapping of field names to their integer values
    // Based on common numeric constant naming patterns
    private static final Map<String, Integer> CONSTANT_MAPPINGS = new HashMap<>();
    
    static {
        // Populate with common numeric constants
        CONSTANT_MAPPINGS.put("ONE", 1);
        CONSTANT_MAPPINGS.put("TWO", 2);
        CONSTANT_MAPPINGS.put("THREE", 3);
        CONSTANT_MAPPINGS.put("FOUR", 4);
        CONSTANT_MAPPINGS.put("FIVE", 5);
        CONSTANT_MAPPINGS.put("SIX", 6);
        CONSTANT_MAPPINGS.put("SEVEN", 7);
        CONSTANT_MAPPINGS.put("EIGHT", 8);
        CONSTANT_MAPPINGS.put("NINE", 9);
        CONSTANT_MAPPINGS.put("TEN", 10);
        CONSTANT_MAPPINGS.put("HUNDRED", 100);
        CONSTANT_MAPPINGS.put("THOUSAND", 1000);
        CONSTANT_MAPPINGS.put("MILLION", 1_000_000);
        CONSTANT_MAPPINGS.put("BILLION", 1_000_000_000);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("Example: java -cp target/classes github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Target class: " + TARGET_CLASS);
        
        String outputDir = sourceDir + "-transformed";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        try {
            launcher.run();
            
            // Find all field accesses
            List<CtFieldAccess<?>> fieldAccesses = launcher.getFactory()
                .getModel()
                .getElements(new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class));
            
            int replacements = 0;
            for (CtFieldAccess<?> fieldAccess : fieldAccesses) {
                // Check if this field access references the target class
                String fieldAccessStr = fieldAccess.toString();
                if (fieldAccessStr.contains(TARGET_CLASS.substring(TARGET_CLASS.lastIndexOf('.') + 1) + ".")) {
                    // Extract field name (e.g., from "Tv.SEVEN" or "com.jcabi.aspects.Tv.SEVEN")
                    String[] parts = fieldAccessStr.split("\\.");
                    String fieldName = parts[parts.length - 1];
                    
                    Integer replacementValue = CONSTANT_MAPPINGS.get(fieldName);
                    
                    if (replacementValue != null) {
                        // Replace field access with integer literal
                        CtLiteral<Integer> literal = launcher.getFactory().createLiteral(replacementValue);
                        fieldAccess.replace(literal);
                        replacements++;
                        System.out.println("Replaced " + fieldAccessStr + " with literal " + replacementValue);
                    } else {
                        System.err.println("Warning: No mapping found for " + fieldAccessStr + 
                                         ". Field '" + fieldName + "' not in CONSTANT_MAPPINGS.");
                    }
                }
            }
            
            // Remove imports of the target class
            int importRemovals = 0;
            for (CtImport ctImport : launcher.getFactory().getModel().getElements(new TypeFilter<CtImport>(CtImport.class))) {
                String importStr = ctImport.toString();
                if (importStr.contains(TARGET_CLASS)) {
                    try {
                        ctImport.delete();
                        importRemovals++;
                        System.out.println("Removed import: " + importStr);
                    } catch (Exception e) {
                        System.err.println("Failed to remove import: " + importStr + " - " + e.getMessage());
                    }
                }
            }
            
            System.out.println("\nTransformation Summary:");
            System.out.println("  Target class: " + TARGET_CLASS);
            System.out.println("  Field replacements: " + replacements);
            System.out.println("  Import removals: " + importRemovals);
            System.out.println("  Constant mappings available: " + CONSTANT_MAPPINGS.size());
            
            // Write transformed code
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            System.out.println("\nTransformed code written to: " + outputDir);
            System.out.println("\nNote: You may need to manually copy transformed files to replace originals.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * For a truly generic solution, this method could be used to parse
     * constant mappings from a configuration file or command-line arguments.
     */
    private static Map<String, Integer> parseConstantMappings(String[] args) {
        // In a more generic implementation, this would parse configuration
        // For now, we use the hardcoded mappings for the Tv class
        return CONSTANT_MAPPINGS;
    }
}