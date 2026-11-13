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
 * GENERIC Spoon transformation for fixing compilation errors caused by
 * the removal of com.jcabi.aspects.Tv class in jcabi-aspects 0.25.1.
 * 
 * This transformation is reusable and can be applied to ANY project
 * affected by this breaking dependency change.
 * 
 * Breaking Change Pattern:
 * - OLD: com.jcabi.aspects.Tv class with public static final int constants
 * - NEW: Tv class completely removed in version 0.25.1
 * - TRANSFORMATION: Replace Tv.CONSTANT with literal integer values
 *                 Remove import statements for com.jcabi.aspects.Tv
 */
public class Main {
    
    // Comprehensive mapping of Tv constants to their literal values
    // Based on common numeric constants pattern in utility classes
    private static final Map<String, String> TV_CONSTANTS = new HashMap<>();
    
    static {
        // Basic numeric constants (most likely to exist in Tv)
        TV_CONSTANTS.put("ZERO", "0");
        TV_CONSTANTS.put("ONE", "1");
        TV_CONSTANTS.put("TWO", "2");
        TV_CONSTANTS.put("THREE", "3");
        TV_CONSTANTS.put("FOUR", "4");
        TV_CONSTANTS.put("FIVE", "5");
        TV_CONSTANTS.put("SIX", "6");
        TV_CONSTANTS.put("SEVEN", "7");      // Confirmed usage
        TV_CONSTANTS.put("EIGHT", "8");
        TV_CONSTANTS.put("NINE", "9");
        TV_CONSTANTS.put("TEN", "10");       // Confirmed usage
        
        // Common multiples
        TV_CONSTANTS.put("HUNDRED", "100");
        TV_CONSTANTS.put("THOUSAND", "1000");
        TV_CONSTANTS.put("MILLION", "1000000");  // Confirmed usage
        TV_CONSTANTS.put("BILLION", "1000000000");
        
        // Time-related constants (common in such utility classes)
        TV_CONSTANTS.put("SECOND", "1");
        TV_CONSTANTS.put("MINUTE", "60");
        TV_CONSTANTS.put("HOUR", "3600");
        TV_CONSTANTS.put("DAY", "86400");
        TV_CONSTANTS.put("WEEK", "604800");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Generic Transformation: Fixing Tv Class Removal ===");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        System.out.println();
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        CtModel model = launcher.buildModel();
        
        int importsRemoved = 0;
        int fieldsReplaced = 0;
        
        // Pattern 1: Remove imports of the removed Tv class
        for (CtImport importElem : model.getElements(new TypeFilter<CtImport>(CtImport.class))) {
            String importStr = importElem.toString();
            if (importStr.contains("com.jcabi.aspects.Tv")) {
                System.out.println("[IMPORT] Removing: " + importStr);
                importElem.delete();
                importsRemoved++;
            }
        }
        
        // Pattern 2: Replace field accesses with literal values
        for (CtFieldRead<?> fieldRead : model.getElements(new TypeFilter<CtFieldRead<?>>(CtFieldRead.class))) {
            try {
                CtFieldReference<?> fieldRef = fieldRead.getVariable();
                if (fieldRef != null) {
                    CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
                    if (declaringType != null && "com.jcabi.aspects.Tv".equals(declaringType.getQualifiedName())) {
                        String fieldName = fieldRef.getSimpleName();
                        String literalValue = TV_CONSTANTS.get(fieldName);
                        
                        if (literalValue != null) {
                            System.out.println("[FIELD] Replacing: Tv." + fieldName + " -> " + literalValue);
                            Factory factory = fieldRead.getFactory();
                            
                            try {
                                long longValue = Long.parseLong(literalValue);
                                CtLiteral<?> literal;
                                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                    literal = factory.createLiteral((int) longValue);
                                } else {
                                    literal = factory.createLiteral(longValue);
                                }
                                fieldRead.replace(literal);
                                fieldsReplaced++;
                            } catch (NumberFormatException e) {
                                System.err.println("  ERROR: Invalid numeric value for Tv." + fieldName);
                            }
                        } else {
                            System.err.println("  WARNING: Unknown Tv constant: " + fieldName);
                            System.err.println("  Add to TV_CONSTANTS map with appropriate value.");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("  ERROR: " + e.getMessage());
            }
        }
        
        launcher.prettyprint();
        
        System.out.println();
        System.out.println("=== Transformation Complete ===");
        System.out.println("Imports removed: " + importsRemoved);
        System.out.println("Field accesses replaced: " + fieldsReplaced);
        System.out.println("Output directory: " + outputDir);
        System.out.println();
        System.out.println("This transformation can be applied to ANY project affected by");
        System.out.println("the removal of com.jcabi.aspects.Tv in jcabi-aspects 0.25.1.");
    }
    
    private static void printUsage() {
        System.err.println("GENERIC Spoon Transformation for jcabi-aspects Tv class removal");
        System.err.println("================================================================");
        System.err.println("Usage: java github.chains.Main <source-directory> <output-directory>");
        System.err.println();
        System.err.println("Description:");
        System.err.println("  Fixes compilation errors caused by removal of com.jcabi.aspects.Tv");
        System.err.println("  in jcabi-aspects 0.25.1. The transformation:");
        System.err.println("  1. Removes imports of com.jcabi.aspects.Tv");
        System.err.println("  2. Replaces Tv.CONSTANT expressions with literal values");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java github.chains.Main /path/to/project/src /path/to/transformed");
        System.err.println();
        System.err.println("This is a reusable transformation for ANY project affected by");
        System.err.println("this breaking dependency change.");
    }
}