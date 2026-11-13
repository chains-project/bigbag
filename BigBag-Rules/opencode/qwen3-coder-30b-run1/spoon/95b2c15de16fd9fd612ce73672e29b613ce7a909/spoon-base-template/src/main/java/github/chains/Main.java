package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // Generic transformation for fixing Jetty API breaking changes
        // Specifically addresses the setHandled(boolean) method call issue
        
        Launcher launcher = new Launcher();
        
        // Set the source directory to transform
        String sourceDirectory = "/workspace/jadler";
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory("/tmp/jadler-fixed");
        
        // Set the Java version
        launcher.getEnvironment().setComplianceLevel(17);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all setHandled invocations on Request objects and remove them
        // This is a common breaking change in Jetty 11 where setHandled became deprecated/removed
        TypeFilter<CtInvocation> filter = new TypeFilter<>(CtInvocation.class);
        int count = 0;
        for (CtInvocation invocation : model.getElements(filter)) {
            // Check if it's a call to setHandled method on a Request object
            if (invocation.getExecutable().getSimpleName().equals("setHandled") &&
                invocation.getTarget() != null &&
                invocation.getTarget().getType().toString().contains("Request")) {
                // Remove the setHandled call - in newer Jetty versions this is no longer needed
                // or has a different mechanism for handling request completion
                CtStatement parent = invocation.getParent(CtStatement.class);
                if (parent != null) {
                    parent.delete();
                    count++;
                }
            }
        }
        
        // Generate the fixed code
        launcher.process();
        
        System.out.println("Transformation completed. Removed " + count + " setHandled calls.");
    }
}