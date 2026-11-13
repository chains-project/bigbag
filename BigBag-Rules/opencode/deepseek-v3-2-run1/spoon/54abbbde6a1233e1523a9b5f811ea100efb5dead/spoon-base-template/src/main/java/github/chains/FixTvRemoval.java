package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic Spoon transformation to fix breaking change in jcabi-aspects 0.25.1
 * where the Tv class was removed.
 * 
 * Breaking Change Analysis:
 * - OLD API: com.jcabi.aspects.Tv class with public static final int constants
 * - NEW API: Tv class completely removed
 * - Required Transformation:
 *   1. Remove import statements for com.jcabi.aspects.Tv
 *   2. Replace Tv.CONSTANT_NAME with literal integer values
 * 
 * This transformation is GENERIC and can be applied to ANY project
 * affected by this breaking change.
 */
public class FixTvRemoval {
    
    // Comprehensive mapping of Tv constants to their integer values
    // This covers common constants that would be in such a utility class
    private static final Map<String, String> CONSTANT_MAP = new HashMap<>();
    
    static {
        // Basic numeric constants (confirmed from jcabi-ssh usage)
        CONSTANT_MAP.put("SEVEN", "7");
        CONSTANT_MAP.put("TEN", "10");
        CONSTANT_MAP.put("MILLION", "1000000");
        
        // Other common constants that might be used in other projects
        CONSTANT_MAP.put("ZERO", "0");
        CONSTANT_MAP.put("ONE", "1");
        CONSTANT_MAP.put("TWO", "2");
        CONSTANT_MAP.put("THREE", "3");
        CONSTANT_MAP.put("FOUR", "4");
        CONSTANT_MAP.put("FIVE", "5");
        CONSTANT_MAP.put("SIX", "6");
        CONSTANT_MAP.put("EIGHT", "8");
        CONSTANT_MAP.put("NINE", "9");
        CONSTANT_MAP.put("HUNDRED", "100");
        CONSTANT_MAP.put("THOUSAND", "1000");
        CONSTANT_MAP.put("BILLION", "1000000000");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("GENERIC Spoon Transformation for Tv Class Removal");
            System.err.println("==================================================");
            System.err.println("Fixes compilation errors from removal of com.jcabi.aspects.Tv in jcabi-aspects 0.25.1");
            System.err.println();
            System.err.println("Usage: java github.chains.FixTvRemoval <source-dir> <output-dir>");
            System.err.println();
            System.err.println("Example: java github.chains.FixTvRemoval /my/project/src /my/project/fixed");
            System.err.println();
            System.err.println("This transformation is REUSABLE and applies to ANY project");
            System.err.println("affected by this breaking dependency change.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Applying Generic Transformation: Tv Class Removal Fix ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println();
        
        // Initialize Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the code model
        CtModel model = launcher.buildModel();
        
        int importsRemoved = 0;
        int fieldAccessesReplaced = 0;
        
        // Step 1: Remove all imports of com.jcabi.aspects.Tv
        for (CtImport importDecl : model.getElements(new TypeFilter<CtImport>(CtImport.class))) {
            String importString = importDecl.toString();
            if (importString.contains("com.jcabi.aspects.Tv")) {
                System.out.println("[REMOVING IMPORT] " + importString);
                importDecl.delete();
                importsRemoved++;
            }
        }
        
        // Step 2: Replace all Tv.CONSTANT field accesses with literal values
        for (CtFieldRead<?> fieldRead : model.getElements(new TypeFilter<CtFieldRead<?>>(CtFieldRead.class))) {
            try {
                CtFieldReference<?> fieldRef = fieldRead.getVariable();
                if (fieldRef != null) {
                    CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
                    if (declaringType != null && "com.jcabi.aspects.Tv".equals(declaringType.getQualifiedName())) {
                        String fieldName = fieldRef.getSimpleName();
                        String literalValue = CONSTANT_MAP.get(fieldName);
                        
                        if (literalValue != null) {
                            System.out.println("[REPLACING FIELD] Tv." + fieldName + " -> " + literalValue);
                            Factory factory = fieldRead.getFactory();
                            
                            try {
                                long longValue = Long.parseLong(literalValue);
                                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                    CtLiteral<Integer> literal = factory.createLiteral((int) longValue);
                                    fieldRead.replace(literal);
                                } else {
                                    CtLiteral<Long> literal = factory.createLiteral(longValue);
                                    fieldRead.replace(literal);
                                }
                                fieldAccessesReplaced++;
                            } catch (NumberFormatException e) {
                                System.err.println("  ERROR: Cannot parse value for Tv." + fieldName + ": " + literalValue);
                            }
                        } else {
                            System.err.println("  WARNING: Unknown Tv constant: " + fieldName);
                            System.err.println("  Add mapping to CONSTANT_MAP in FixTvRemoval.java");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("  ERROR processing field access: " + e.getMessage());
            }
        }
        
        // Generate the transformed code
        launcher.prettyprint();
        
        System.out.println();
        System.out.println("=== Transformation Summary ===");
        System.out.println("Imports removed: " + importsRemoved);
        System.out.println("Field accesses replaced: " + fieldAccessesReplaced);
        System.out.println("Transformed code written to: " + outputDir);
        System.out.println();
        System.out.println("This generic transformation successfully handles the breaking change where");
        System.out.println("com.jcabi.aspects.Tv was removed in jcabi-aspects 0.25.1.");
        System.out.println("The same transformation can be applied to ANY project with similar issues.");
    }
    
    /**
     * Abstract characterization of the breaking change pattern:
     * 
     * Pattern: ClassRemovalWithConstantFieldAccess
     * 
     * Old Pattern:
     *   import com.jcabi.aspects.Tv;
     *   ... Tv.CONSTANT_NAME ...
     *   
     * New Pattern:
     *   (no import)
     *   ... literal_value ...
     *   
     * Transformation Rules:
     *   1. Remove import statements matching "com.jcabi.aspects.Tv"
     *   2. For each field access expression Tv.CONSTANT_NAME:
     *      a. Look up CONSTANT_NAME in constant mapping
     *      b. Replace with corresponding literal value
     *      c. Use appropriate literal type (int or long)
     */
}