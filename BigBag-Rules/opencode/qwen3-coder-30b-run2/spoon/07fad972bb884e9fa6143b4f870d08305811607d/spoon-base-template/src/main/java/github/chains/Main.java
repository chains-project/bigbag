package github.chains;

import spoon.Launcher;

/**
 * Generic Spoon transformation to fix Jakarta EE migration issues in logback-classic
 * This transformation handles the migration from javax.* to jakarta.* packages
 * caused by upgrading logback-classic from 1.2.x to 1.4.1
 */
public class Main {
    public static void main(String[] args) {
        // Process the pay-adminusers project
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/pay-adminusers/src");
        launcher.setSourceOutputDirectory("/workspace/pay-adminusers/src");
        
        // Build the model and process
        launcher.buildModel();
        launcher.process();
        
        System.out.println("Spoon transformation completed.");
    }
}