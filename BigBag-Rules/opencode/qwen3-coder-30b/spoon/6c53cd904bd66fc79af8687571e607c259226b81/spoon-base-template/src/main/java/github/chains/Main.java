package github.chains;

import spoon.Launcher;

/**
 * Generic Spoon transformation rule to fix breaking API changes in dependencies.
 * This rule specifically addresses changes in ActionProxyFactory API from Struts2.
 * 
 * Breaking change pattern:
 * Old: createActionProxy(String actionName, String namespace, String methodName, Map params)
 * New: createActionProxy(String actionName, String namespace, String methodName, Map params, boolean executeResult, boolean cleanupContext)
 * 
 * This transformation adds the two missing boolean parameters to all invocations
 * of createActionProxy with the old signature.
 * 
 * Usage: java -cp <classpath> github.chains.Main <source_directory>
 */
public class Main {
    public static void main(String[] args) {
        // Check if arguments are provided
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/transformed");
        
        // Process the code
        launcher.process();
        
        System.out.println("Transformation completed. Output written to: " + sourceDirectory + "/transformed");
    }
}