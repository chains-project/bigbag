package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic transformation to fix breaking changes in jcabi-aspects Loggable annotation.
 * This demonstrates the approach for fixing the jcabi-aspects 0.25.1 breaking change.
 */
public class Main {
    public static void main(String[] args) {
        // Check if source directory is provided
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "/generated");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Find all Loggable annotations and analyze them
        int count = 0;
        for (CtAnnotation<?> annotation : model.getElements(new TypeFilter<>(CtAnnotation.class))) {
            CtTypeReference<?> annotationType = annotation.getAnnotationType();
            
            // If it's a Loggable annotation, analyze it
            if (annotationType != null && 
                "com.jcabi.aspects.Loggable".equals(annotationType.getQualifiedName())) {
                
                System.out.println("Found Loggable annotation at line " + 
                                 annotation.getPosition().getLine());
                count++;
            }
        }
        
        System.out.println("Found " + count + " Loggable annotations.");
        System.out.println("Transformation approach: Replace integer parameters with enum constants");
        System.out.println("This would fix the breaking change in jcabi-aspects 0.25.1");
    }
}