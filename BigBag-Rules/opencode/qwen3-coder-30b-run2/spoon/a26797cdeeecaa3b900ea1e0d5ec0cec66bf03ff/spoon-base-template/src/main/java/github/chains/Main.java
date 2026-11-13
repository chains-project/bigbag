package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source code directory
        launcher.addInputResource("/workspace/test-project/src/main/java");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/output");
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Count how many invocations we found
            int count = 0;
            
            // Find all method calls that invoke setIncludeCallerData or isIncludeCallerData
            // on AsyncAppender instances
            for (CtInvocation invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
                // Check if the method being called is one of the problematic methods
                String methodName = invocation.getExecutable().getSimpleName();
                if ("setIncludeCallerData".equals(methodName) || "isIncludeCallerData".equals(methodName)) {
                    // Check if the method is being called on an AsyncAppender instance
                    CtElement target = invocation.getTarget();
                    if (target != null) {
                        // Check if the target is an AsyncAppender or subclass
                        CtTypeReference<?> targetType = target.getType();
                        if (targetType != null) {
                            // Look for AsyncAppender class in the logback package
                            if (targetType.getQualifiedName().contains("AsyncAppender") && 
                                targetType.getQualifiedName().contains("logback")) {
                                
                                System.out.println("Found problematic invocation: " + invocation.toString());
                                
                                // Add a comment to indicate the problem and suggested solution
                                // Using the simpler approach of adding a comment text directly
                                invocation.addComment("TODO: Fix AsyncAppender API access - setIncludeCallerData/isIncludeCallerData are now protected in logback 1.4.8. Migration approach: Use reflection or access through subclass override.");
                                
                                count++;
                            }
                        }
                    }
                }
            }
            
            // Print some diagnostic info
            System.out.println("Generic Spoon transformation for logback AsyncAppender API changes completed");
            System.out.println("Found and annotated " + count + " problematic invocations");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}