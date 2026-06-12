package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        System.out.println("Flyway transformation tool - Basic version");
        
        // Configure Spoon
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Add the source directory to process
        launcher.addInputResource("/workspace/nem");
        
        // Process the code
        launcher.buildModel();
        
        // Write back the modified code
        launcher.setSourceOutputDirectory("/workspace/nem-fixed");
        launcher.process();
        
        System.out.println("Flyway transformation completed!");
    }
}