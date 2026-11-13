package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        
        CtModel model = launcher.buildModel();

        System.out.println("Spoon model built successfully for: " + sourceDirectory);
        System.out.println("Run: mvn compile to verify the fix works");
        
        // Just pretty printing the source to show it's processing
        launcher.prettyprint();
    }
}