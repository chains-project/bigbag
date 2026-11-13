package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        // Example usage of the transformation
        System.out.println("Flyway Upgrade Transformation Tool");
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Process the Java files in the given directory
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "_fixed");
        launcher.addProcessor(new FlywayUpgradeProcessor());
        
        // Process the code
        CtModel model = launcher.buildModel();
        launcher.process();
        
        // Print a success message
        System.out.println("Flyway API transformation completed successfully.");
        System.out.println("Fixed files are in: " + sourceDir + "_fixed");
    }
}