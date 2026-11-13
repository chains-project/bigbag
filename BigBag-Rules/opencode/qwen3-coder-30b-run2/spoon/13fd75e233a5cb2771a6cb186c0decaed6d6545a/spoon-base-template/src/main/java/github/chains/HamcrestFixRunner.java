package github.chains;

import spoon.Launcher;
import spoon.processing.Processor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;

import java.io.File;

/**
 * Runner for the generic Hamcrest API fix transformation.
 * This can be used to apply the transformation to any Maven project.
 */
public class HamcrestFixRunner {

    public static void main(String[] args) {
        // Default to the docker-adapter project if no argument is provided
        String projectPath = args.length > 0 ? args[0] : "/workspace/docker-adapter";
        
        System.out.println("Applying Hamcrest API fix to project: " + projectPath);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectPath + "/src/main/java");
        launcher.addInputResource(projectPath + "/src/test/java");
        launcher.setSourceOutputDirectory(projectPath + "/src-gen");
        
        // Add the processor to fix Hamcrest API issues
        launcher.addProcessor(new Main.HamcrestFixProcessor());
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Process the model
        launcher.process();
        
        // Print information about what was processed
        System.out.println("Processing completed for " + projectPath);
        System.out.println("Check " + projectPath + "/src-gen for modified sources");
        
        // Try to compile the modified code
        try {
            launcher.getModel().getElements(new spoon.reflect.visitor.filter.TypeFilter<>(CtClass.class))
                .forEach(cls -> {
                    System.out.println("Processed class: " + cls.getQualifiedName());
                });
        } catch (Exception e) {
            System.err.println("Error processing classes: " + e.getMessage());
        }
    }
}