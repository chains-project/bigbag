package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to process
        launcher.addInputResource("/workspace/billy");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/spoon-output");
        
        // Build the model
        launcher.buildModel();
        
        // Get the spoon model
        var model = launcher.getFactory().getModel();
        
        // Find all FopFactory.newInstance() calls with no arguments
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                // Check if it's a call to FopFactory.newInstance() with no arguments
                return element.getExecutable().getSimpleName().equals("newInstance") &&
                       element.getTarget() != null &&
                       element.getTarget().getType().getQualifiedName().equals("org.apache.fop.apps.FopFactory") &&
                       element.getArguments().isEmpty();
            }
        });
        
        // Transform each invocation - replace with the new API
        for (CtInvocation invocation : invocations) {
            // FOP 2.2 requires FopFactory.newInstance(FopFactoryConfig)
            // We'll pass null as the config parameter as a safe default
            CtLiteral<?> nullLiteral = launcher.getFactory().createLiteral(null);
            invocation.addArgument(nullLiteral);
        }
        
        // Write the modified code back to files
        launcher.process();
        
        System.out.println("Transformation completed. Found " + invocations.size() + " FopFactory.newInstance() calls.");
    }
}