package github.chains;

import spoon.Launcher;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

/**
 * Generic transformation to fix breaking changes in pm-wicket-utils 5.0
 * Specifically addresses removal of javax.validation and org.apache.commons.beanutils
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        System.out.println("Processing source directory: " + sourceDirectory);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        
        // Add the source directory
        try {
            launcher.addInputResource(SpoonResourceHelper.createResource(new File(sourceDirectory)));
        } catch (Exception e) {
            System.err.println("Error adding input resource: " + e.getMessage());
            System.exit(1);
        }
        
        // Add the processor
        launcher.addProcessor(new ValidationFixProcessor());
        
        // Process
        CtModel model = launcher.buildModel();
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
}