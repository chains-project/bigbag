package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.processing.AbstractProcessor;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

/**
 * Generic Spoon transformation rule to migrate from javax.validation to jakarta.validation
 * This rule handles the breaking change in Hibernate Validator 8.0+ where validation annotations
 * were moved from javax.validation to jakarta.validation package.
 * 
 * Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main /path/to/source/directory
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_processed");
        launcher.getEnvironment().setComplianceLevel(11);
        
        // Add our transformation processor
        launcher.addProcessor(new AbstractProcessor<CtAnnotation<?>>() {
            @Override
            public void process(CtAnnotation<?> annotation) {
                // Check if the annotation is from the old package
                String annotationQualifiedName = annotation.getAnnotationType().getQualifiedName();
                
                if (annotationQualifiedName.startsWith("javax.validation.")) {
                    // Replace the package reference
                    String newQualifiedName = annotationQualifiedName.replace("javax.validation.", "jakarta.validation.");
                    Factory factory = getFactory();
                    annotation.setAnnotationType(factory.Type().createReference(newQualifiedName));
                }
            }
        });
        
        // Build the model and process
        launcher.buildModel();
        launcher.process();
        
        System.out.println("Transformation completed successfully!");
        System.out.println("Processed files are in: " + sourceDirectory + "_processed");
    }
}