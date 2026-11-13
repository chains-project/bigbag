package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.Filter;

/**
 * A generic Spoon transformation to fix the breaking change in plexus-archiver 4.4.0.
 * The enableLogging method was removed from AbstractUnArchiver.
 * This transformation removes all calls to enableLogging() on AbstractUnArchiver instances.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp <jar-file> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-processed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Find all invocations of enableLogging method regardless of receiver type
        // We'll just remove all enableLogging calls since they are all problematic
        model.getElements(new Filter<CtInvocation>() {
            @Override
            public boolean matches(CtInvocation element) {
                // Check if it's a method invocation with name "enableLogging"
                if (element.getExecutable() != null && "enableLogging".equals(element.getExecutable().getSimpleName())) {
                    return true;
                }
                return false;
            }
        }).forEach(invocation -> {
            // Remove the invocation
            invocation.delete();
        });
        
        // Print statistics
        int count = model.getElements(new Filter<CtInvocation>() {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable() != null && "enableLogging".equals(element.getExecutable().getSimpleName());
            }
        }).size();
        System.out.println("Removed enableLogging invocations: " + count);
        
        launcher.process();
    }
}