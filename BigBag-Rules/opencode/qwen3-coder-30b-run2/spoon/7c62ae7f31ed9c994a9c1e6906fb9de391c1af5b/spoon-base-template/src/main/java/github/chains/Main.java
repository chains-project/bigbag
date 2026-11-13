package github.chains;

import spoon.Launcher;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;

import java.io.File;
import java.util.Collections;

/**
 * Main entry point for the Hibernate breaking change fix tool.
 * This class sets up the Spoon processor to find and fix problematic Hibernate API calls.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory
        try {
            launcher.addInputResource(SpoonResourceHelper.createResource(new File(sourceDirectory)));
        } catch (Exception e) {
            System.err.println("Failed to add input resource: " + e.getMessage());
            System.exit(1);
        }
        
        // Set the output directory
        launcher.setSourceOutputDirectory("./transformed-output");
        
        // Add our processor
        launcher.addProcessor(new GenericHibernateFixProcessor());
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Run the processors
        launcher.process();
        
        System.out.println("Transformation completed.");
    }
}