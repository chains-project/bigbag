package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation rule to replace getArgumentAt(int, Class) calls
 * with getArgument(int, Class) in Mockito tests.
 */
public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source code directory to analyze
        launcher.addInputResource("src/test/java");
        
        // Set the output directory for processed files
        launcher.setSourceOutputDirectory("src/test/java");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations that match the pattern
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        
        // Process each invocation
        for (CtInvocation<?> invocation : invocations) {
            // Check if it's a call to getArgumentAt with correct signature
            if (isGetArgumentAtCall(invocation)) {
                // Replace with getArgument call using correct Spoon API
                try {
                    // Use the correct API to change the method name
                    invocation.getExecutable().setSimpleName("getArgument");
                } catch (Exception e) {
                    // Handle any exceptions gracefully
                    // In production, you might want more sophisticated error handling
                }
            }
        }
        
        // Pretty-print the modified model back to files
        launcher.prettyprint();
    }
    
    /**
     * Checks if the invocation is a call to getArgumentAt(int, Class)
     */
    private static boolean isGetArgumentAtCall(CtInvocation<?> invocation) {
        // Get the executable reference
        CtExecutableReference<?> executable = invocation.getExecutable();
        
        // Check if method name is getArgumentAt
        if (executable == null || !executable.getSimpleName().equals("getArgumentAt")) {
            return false;
        }
        
        // Check if it has exactly 2 arguments (int, Class)
        if (invocation.getArguments().size() != 2) {
            return false;
        }
        
        return true;
    }
}