package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Add input source
        launcher.addInputResource(sourceDir);
        
        // Set source classpath to include tinspin-indexes
        launcher.getEnvironment().setSourceClasspath(
            new String[] {"/root/.m2/repository/org/tinspin/tinspin-indexes/1.8.0/tinspin-indexes-1.8.0.jar"});
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Add processor
        launcher.addProcessor(new TinspinDeprecatedFixer());
        
        // Process
        launcher.process();
        
        // Output transformed code (in-place)
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
}