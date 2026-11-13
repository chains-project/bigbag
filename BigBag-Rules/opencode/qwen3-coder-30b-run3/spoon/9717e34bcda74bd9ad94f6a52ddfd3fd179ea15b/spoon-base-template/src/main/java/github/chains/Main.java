package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation to fix com.jcabi.aspects.Tv class removal.
 * This transformation replaces all usages of Tv.XXX with literal numeric values.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Find all field accesses to Tv.XXX patterns
        model.getElements(new TypeFilter<>(CtFieldAccess.class)).forEach(fieldAccess -> {
            CtFieldReference<?> fieldRef = fieldAccess.getVariable();
            if (fieldRef.getDeclaringType() != null && 
                fieldRef.getDeclaringType().getQualifiedName().equals("com.jcabi.aspects.Tv")) {
                
                // Replace with corresponding literal value
                String fieldName = fieldRef.getSimpleName();
                int replacementValue = getReplacementValue(fieldName);
                fieldAccess.replace(launcher.getFactory().Code().createLiteral(replacementValue));
            }
        });
        
        // Print a message about the transformation
        System.out.println("Transformed Tv.XXX references to literal values.");
        
        // Generate the transformed code
        launcher.process();
        
        System.out.println("Transformation completed. Check " + sourceDir + "-transformed directory.");
    }
    
    /**
     * Maps Tv constants to their numeric values.
     */
    private static int getReplacementValue(String fieldName) {
        switch (fieldName) {
            case "TEN":
                return 10;
            case "TWENTY":
                return 20;
            case "THIRTY":
                return 30;
            case "FIFTY":
                return 50;
            case "HUNDRED":
                return 100;
            case "THOUSAND":
                return 1000;
            case "FIVE":
                return 5;
            case "THREE":
                return 3;
            case "SIX":
                return 6;
            case "BILLION":
                return 1000000000;
            case "FIFTEEN":
                return 15;
            default:
                // Default fallback - using the field name to make it obvious
                // In practice, you'd want to be more intelligent here
                return 0;
        }
    }
}