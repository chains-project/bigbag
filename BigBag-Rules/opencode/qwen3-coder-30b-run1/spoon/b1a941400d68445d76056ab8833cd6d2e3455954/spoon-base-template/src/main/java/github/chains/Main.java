package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

/**
 * Main class to execute the SnakeYAML 2.0 fix transformation on the fluxtion project
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/fixed");
        
        // Add the processor to fix SnakeYAML constructor calls
        launcher.addProcessor(new SnakeYamlFixProcessor());
        
        // Process the code
        CtModel model = launcher.buildModel();
        launcher.process();
        
        // Print a summary
        System.out.println("SnakeYAML 2.0 fix transformation completed on " + sourceDirectory);
    }
}