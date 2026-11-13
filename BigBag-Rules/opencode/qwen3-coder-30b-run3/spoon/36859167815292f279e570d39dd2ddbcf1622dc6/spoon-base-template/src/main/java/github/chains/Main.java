package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        
        // Set the source directory to analyze
        launcher.addInputResource("/workspace/billy");
        
        // Set the output directory for processed files
        launcher.setSourceOutputDirectory("/workspace/billy-transformed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all calls to FopFactory.newInstance() with no arguments
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                // Check if it's a call to FopFactory.newInstance() with no arguments
                if (element.getExecutable() != null) {
                    CtExecutableReference<?> executableRef = element.getExecutable();
                    String signature = executableRef.getSignature();
                    
                    // Match the method call with no arguments
                    if (signature.equals("newInstance()")) {
                        // Get the target (the class we're calling the method on)
                        if (element.getTarget() != null) {
                            // Ensure it's FopFactory
                            if (element.getTarget().getType() != null) {
                                CtTypeReference<?> targetType = element.getTarget().getType();
                                return targetType.getQualifiedName().equals("org.apache.fop.apps.FopFactory");
                            }
                        }
                    }
                }
                return false;
            }
        });
        
        // Transform each invocation
        for (CtInvocation<?> invocation : invocations) {
            // Create a new invocation that calls newInstance(null) instead
            CtInvocation<?> newInvocation = invocation.clone();
            newInvocation.getArguments().clear();
            // Add a null argument to match the new API signature
            newInvocation.addArgument(launcher.getFactory().createLiteral(null));
            
            // Replace the old invocation with the new one
            invocation.replace(newInvocation);
        }
        
        // Write the transformed code to the output directory
        launcher.process();
    }
}