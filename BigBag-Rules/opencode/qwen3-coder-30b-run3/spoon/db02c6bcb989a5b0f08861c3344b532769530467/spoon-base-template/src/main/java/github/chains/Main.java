package github.chains;

import spoon.Launcher;

/**
 * Generic Spoon transformation to fix Hamcrest constructor signature issues.
 * This fixes cases where StringContains and StringStartsWith constructors
 * were changed from (boolean, String) to just (String).
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.getEnvironment().setComplianceLevel(17);
        
        launcher.run();
        
        // Save the modified code back to files
        launcher.prettyprint();
    }
}