package github.chains;

import spoon.Launcher;
import spoon.processing.Processor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Main <source-directory> <target-directory>");
            return;
        }
        
        String sourceDir = args[0];
        String targetDir = args[1];
        
        System.out.println("Migrating validation API from javax.validation to jakarta.validation");
        System.out.println("Source: " + sourceDir);
        System.out.println("Target: " + targetDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(targetDir);
        
        // Add our processor
        launcher.addProcessor(new ValidationMigrationProcessor());
        
        // Process the code
        CtModel model = launcher.buildModel();
        launcher.process();
        
        // Print a summary
        System.out.println("Migration completed successfully");
    }
}
}