package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        // Set up Spoon launcher
        Launcher launcher = new Launcher();
        
        // Add source folders
        launcher.addInputResource("src/main/resources");
        
        // Add Spoon template processor
        launcher.addProcessor(new ScriptResultRemovalProcessor());
        
        // Build and process
        launcher.buildModel();
        launcher.process();
        
        // Print the modified code
        launcher.prettyprint();
    }
}