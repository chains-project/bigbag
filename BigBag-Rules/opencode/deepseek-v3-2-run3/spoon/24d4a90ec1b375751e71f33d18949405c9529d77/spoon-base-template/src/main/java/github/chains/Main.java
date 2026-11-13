package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.util.HashMap;
import java.util.Map;

public class Main {
    // Mapping from Tv field names to their integer values
    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();
    static {
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
        // Add other constants as needed
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        Launcher launcher = new Launcher();
        // Don't use noClasspath - we want to preserve imports
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        CtModel model = launcher.buildModel();
        
        // Transform the model
        transformModel(model, launcher.getFactory());
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to: " + outputDir);
    }
    
    private static void transformModel(CtModel model, Factory factory) {
        // Find and replace all field accesses to com.jcabi.aspects.Tv
        model.getElements(new TypeFilter<>(CtFieldAccess.class)).forEach(fieldAccess -> {
            try {
                CtTypeReference<?> fieldDeclaringType = fieldAccess.getVariable().getDeclaringType();
                if (fieldDeclaringType != null && 
                    "com.jcabi.aspects.Tv".equals(fieldDeclaringType.getQualifiedName())) {
                    
                    String fieldName = fieldAccess.getVariable().getSimpleName();
                    Integer replacementValue = TV_CONSTANTS.get(fieldName);
                    
                    if (replacementValue != null) {
                        // Create a literal integer to replace the field access
                        CtLiteral<Integer> literal = factory.createLiteral(replacementValue);
                        
                        // Replace the field access with the literal
                        fieldAccess.replace(literal);
                        System.out.println("Replaced " + fieldDeclaringType.getQualifiedName() 
                            + "." + fieldName + " with " + replacementValue);
                    } else {
                        System.err.println("Warning: Unknown Tv constant: " + fieldName 
                            + ". Replacing with 0.");
                        CtLiteral<Integer> literal = factory.createLiteral(0);
                        fieldAccess.replace(literal);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing field access: " + e.getMessage());
            }
        });
        
        // Note: Imports will be automatically removed by Spoon when they become unused
        // after we replace all field accesses
    }
}