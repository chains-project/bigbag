package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

/**
 * Generic transformation to fix Tv.SEVEN and similar constants 
 * in RetryOnFailure annotations.
 */
public class Main {
    public static void main(String[] args) {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jcabi-ssh/src/main/java";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        CtModel model = launcher.buildModel();
        
        // Find all RetryOnFailure annotations and fix Tv constants
        model.getElements(new TypeFilter<CtAnnotation<?>>(CtAnnotation.class) {
            @Override
            public boolean matches(CtAnnotation<?> element) {
                return element.getAnnotationType().getSimpleName().equals("RetryOnFailure");
            }
        }).forEach(annotation -> {
            // Find all elements in the annotation that reference Tv constants
            annotation.getElements(new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class) {
                @Override
                public boolean matches(CtFieldAccess<?> element) {
                    return element.getVariable().getSimpleName().equals("Tv");
                }
            }).forEach(fieldAccess -> {
                // Replace Tv.SEVEN with 7, Tv.TEN with 10, Tv.MILLION with 1000000
                String constantName = fieldAccess.getVariable().getSimpleName();
                int replacementValue = 0;
                boolean shouldReplace = true;
                
                switch (constantName) {
                    case "SEVEN":
                        replacementValue = 7;
                        break;
                    case "TEN":
                        replacementValue = 10;
                        break;
                    case "MILLION":
                        replacementValue = 1000000;
                        break;
                    default:
                        shouldReplace = false;
                        break;
                }
                
                if (shouldReplace) {
                    // Replace the field access with the integer literal
                    CtExpression<Integer> replacement = 
                        annotation.getFactory().createLiteral(replacementValue);
                    fieldAccess.replace(replacement);
                }
            });
        });
        
        // Write the modified files back to the source directory
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyPrint();
        
        // Output the transformed code to stdout for verification
        System.out.println("Transformation completed successfully. Model contains " + 
                          model.getAllTypes().size() + " types.");
    }
}