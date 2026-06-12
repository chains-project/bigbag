package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // Check if input directory is provided
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/generated");
        
        // Process the model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations of enableLogging and remove them
        model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                // Match method calls to enableLogging
                CtExecutableReference<?> executableRef = element.getExecutable();
                return executableRef != null && 
                       "enableLogging".equals(executableRef.getSimpleName());
            }
        }).forEach(invocation -> {
            // Since the method was removed in plexus-archiver 4.4.0, we can safely remove it
            invocation.delete();
        });
        
        // Generate the modified code
        launcher.process();
        
        System.out.println("Transformation completed successfully. Modified files are in " + sourceDirectory + "/generated");
    }
}