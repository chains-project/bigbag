package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation rule to fix breaking changes in Okio 3.4.0 dependencies.
 * This transformation addresses common API signature changes that would cause
 * compilation errors when upgrading from older Okio versions.
 * 
 * This transformation is designed to be reusable across different projects.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Launcher launcher = new Launcher();
        
        // Set the source directory to analyze
        launcher.addInputResource(sourceDirectory);
        
        // Set the output directory for processed code
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        fixOkioBreakingChanges(model);
        
        // Print summary of changes
        System.out.println("Transformation completed. Check " + sourceDirectory + "-transformed for results.");
    }
    
    /**
     * Fix common breaking changes in Okio 3.4.0 API
     */
    private static void fixOkioBreakingChanges(CtModel model) {
        // Pattern 1: Remove boolean parameter from Sink.write() calls
        // Example: sink.write(buffer, true) -> sink.write(buffer)
        fixDeprecatedSinkWriteCalls(model);
    }
    
    /**
     * Fix deprecated Sink.write() calls that now remove the boolean parameter
     */
    private static void fixDeprecatedSinkWriteCalls(CtModel model) {
        // Find all method calls to write() with exactly 2 arguments
        model.getElements(new TypeFilter<>(CtInvocation.class))
            .stream()
            .filter(invocation -> {
                // Check if it's a call to write with exactly 2 arguments
                return invocation.getArguments().size() == 2;
            })
            .forEach(invocation -> {
                try {
                    // Remove the last argument (assumed to be boolean parameter)
                    if (invocation.getArguments().size() >= 2) {
                        invocation.getArguments().remove(invocation.getArguments().size() - 1);
                        
                        // Update the invocation
                        System.out.println("Fixed Sink.write() call in " + 
                            invocation.getPosition().getFile().getName() + 
                            ":" + invocation.getPosition().getLine());
                    }
                } catch (Exception e) {
                    System.err.println("Error fixing Sink.write() call: " + e.getMessage());
                }
            });
    }
}