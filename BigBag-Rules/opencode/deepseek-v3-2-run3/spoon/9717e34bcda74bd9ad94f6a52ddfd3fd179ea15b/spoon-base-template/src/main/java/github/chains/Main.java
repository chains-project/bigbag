package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class Main {
    
    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();
    
    static {
        TV_CONSTANTS.put("TEN", 10);
        TV_CONSTANTS.put("TWENTY", 20);
        TV_CONSTANTS.put("THIRTY", 30);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
        TV_CONSTANTS.put("BILLION", 1000000000);
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("FOUR", 4);
        TV_CONSTANTS.put("THREE", 3);
        TV_CONSTANTS.put("SIX", 6);
        TV_CONSTANTS.put("FIFTEEN", 15);
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setLevel("OFF"); // Reduce logging
        
        // Add input resource
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all field accesses
        List<CtFieldAccess> fieldAccesses = model.getElements(new TypeFilter<>(CtFieldAccess.class));
        
        int replacements = 0;
        for (CtFieldAccess fieldAccess : fieldAccesses) {
            CtFieldReference<?> fieldRef = fieldAccess.getVariable();
            if (fieldRef != null) {
                CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
                if (declaringType != null && 
                    "com.jcabi.aspects.Tv".equals(declaringType.getQualifiedName())) {
                    
                    String fieldName = fieldRef.getSimpleName();
                    
                    if (TV_CONSTANTS.containsKey(fieldName)) {
                        int value = TV_CONSTANTS.get(fieldName);
                        
                        // Create a literal integer to replace the field access
                        CtLiteral<Integer> literal = launcher.getFactory().createLiteral(value);
                        
                        // Replace the field access with the literal
                        fieldAccess.replace(literal);
                        
                        System.out.println("Replaced Tv." + fieldName + " with " + value);
                        replacements++;
                    }
                }
            }
        }
        
        // Generate transformed code
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete! Replaced " + replacements + " occurrences.");
    }
}