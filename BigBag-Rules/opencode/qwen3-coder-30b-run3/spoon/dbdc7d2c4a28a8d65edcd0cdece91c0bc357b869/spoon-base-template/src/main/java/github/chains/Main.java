package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // Create launcher
        Launcher launcher = new Launcher();
        
        // Set the source directories
        launcher.addInputResource("/workspace/ci-sauce/src/main/java");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all invocations of enableLogging method
        model.getElements(new TypeFilter<>(CtInvocation.class))
              .stream()
              .filter(invocation -> invocation.getExecutable().getSimpleName().equals("enableLogging"))
              .forEach(invocation -> {
                  // Get the method that contains this invocation
                  CtMethod<?> parentMethod = invocation.getParent(CtMethod.class);
                  
                  // Remove the enableLogging call - this is the fix for the breaking change
                  // Since enableLogging was removed in plexus-archiver 4.4.0, we simply remove the call
                  invocation.delete();
                  System.out.println("Removed enableLogging call from method: " + parentMethod.getSimpleName());
              });
        
        System.out.println("Transformation completed successfully.");
    }
}