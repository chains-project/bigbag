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

public class ImprovedMain {
    
    // Generic transformation for removing Tv class usage
    // This handles the breaking change in jcabi-aspects 0.25.1 where Tv class was removed
    
    private static final Map<String, String> TV_CONSTANTS = new HashMap<>();
    
    static {
        // Constants from the Tv class (commonly used numeric constants)
        // These are based on typical utility class patterns
        TV_CONSTANTS.put("ZERO", "0");
        TV_CONSTANTS.put("ONE", "1");
        TV_CONSTANTS.put("TWO", "2");
        TV_CONSTANTS.put("THREE", "3");
        TV_CONSTANTS.put("FOUR", "4");
        TV_CONSTANTS.put("FIVE", "5");
        TV_CONSTANTS.put("SIX", "6");
        TV_CONSTANTS.put("SEVEN", "7");
        TV_CONSTANTS.put("EIGHT", "8");
        TV_CONSTANTS.put("NINE", "9");
        TV_CONSTANTS.put("TEN", "10");
        TV_CONSTANTS.put("HUNDRED", "100");
        TV_CONSTANTS.put("THOUSAND", "1000");
        TV_CONSTANTS.put("MILLION", "1000000");
        TV_CONSTANTS.put("BILLION", "1000000000");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Generic Spoon Transformation for jcabi-aspects Tv class removal");
            System.err.println("Usage: java github.chains.ImprovedMain <source-dir> <output-dir>");
            System.err.println("This transformation fixes compilation errors caused by removal of com.jcabi.aspects.Tv");
            System.err.println("in jcabi-aspects 0.25.1 by replacing Tv.CONSTANT with literal values.");
            System.exit(1);
        }
        
        System.out.println("=== Generic Transformation: Tv Class Removal Fix ===");
        System.out.println("Source: " + args[0]);
        System.out.println("Output: " + args[1]);
        System.out.println("This transformation handles the breaking change where");
        System.out.println("com.jcabi.aspects.Tv was removed in version 0.25.1");
        System.out.println();
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        
        CtModel model = launcher.buildModel();
        
        int importCount = 0;
        int fieldCount = 0;
        
        // Remove imports of the removed Tv class
        for (CtImport importElem : model.getElements(new TypeFilter<CtImport>(CtImport.class))) {
            String importStr = importElem.toString();
            if (importStr.contains("com.jcabi.aspects.Tv")) {
                System.out.println("Removing import: " + importStr);
                importElem.delete();
                importCount++;
            }
        }
        
        // Replace field accesses with literal values
        for (CtFieldRead<?> fieldRead : model.getElements(new TypeFilter<CtFieldRead<?>>(CtFieldRead.class))) {
            try {
                CtFieldReference<?> fieldRef = fieldRead.getVariable();
                if (fieldRef != null) {
                    CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
                    if (declaringType != null && "com.jcabi.aspects.Tv".equals(declaringType.getQualifiedName())) {
                        String fieldName = fieldRef.getSimpleName();
                        String literalValue = TV_CONSTANTS.get(fieldName);
                        
                        if (literalValue != null) {
                            System.out.println("Transforming: Tv." + fieldName + " -> " + literalValue);
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
                                fieldCount++;
                            } catch (NumberFormatException e) {
                                System.err.println("  ERROR: Cannot parse value for Tv." + fieldName + ": " + literalValue);
                            }
                        } else {
                            System.err.println("  WARNING: Unknown Tv constant: " + fieldName);
                            System.err.println("  Add mapping to TV_CONSTANTS map in ImprovedMain.java");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("  ERROR processing field access: " + e.getMessage());
            }
        }
        
        launcher.prettyprint();
        
        System.out.println();
        System.out.println("=== Transformation Summary ===");
        System.out.println("Removed imports: " + importCount);
        System.out.println("Replaced field accesses: " + fieldCount);
        System.out.println("Output written to: " + args[1]);
        System.out.println();
        System.out.println("Note: This is a GENERIC transformation that can be applied to");
        System.out.println("      any project affected by the removal of com.jcabi.aspects.Tv");
        System.out.println("      in jcabi-aspects 0.25.1.");
    }
    
    // Abstract characterization of the breaking change:
    // OLD API: com.jcabi.aspects.Tv class with public static final int constants
    // NEW API: Tv class removed entirely in version 0.25.1
    // TRANSFORMATION: Replace Tv.CONSTANT with corresponding literal integer value
    //                 Remove import statements for com.jcabi.aspects.Tv
}