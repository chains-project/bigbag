package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtConstructorCall;
import java.net.URI;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-directory> <output-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        System.out.println("Applying FOP 2.2 API migration from: " + sourceDir + " to: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements((CtInvocation<?> invocation) -> {
            // Check if this is a static method call to FopFactory.newInstance()
            if (invocation.getExecutable() != null && 
                invocation.getExecutable().isStatic()) {
                
                // Get the target type
                CtTypeReference<?> targetType = invocation.getExecutable().getDeclaringType();
                if (targetType != null && 
                    "org.apache.fop.apps.FopFactory".equals(targetType.getQualifiedName())) {
                    
                    // Check if method name is "newInstance"
                    if ("newInstance".equals(invocation.getExecutable().getSimpleName())) {
                        // Check if it has no arguments (the old API)
                        if (invocation.getArguments().isEmpty()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
        
        System.out.println("Found " + invocations.size() + " occurrences of FopFactory.newInstance()");
        
        // Transform each invocation
        for (CtInvocation<?> invocation : invocations) {
            System.out.println("  Fixing: " + invocation.getPosition());
            
            // Create URI literal: "."
            CtLiteral<String> uriLiteral = factory.createLiteral(".");
            
            // Create constructor call: new URI(".")
            CtTypeReference<URI> uriTypeRef = factory.Type().createReference(URI.class);
            
            // Create a simple constructor call
            CtConstructorCall<URI> uriConstructorCall = factory.Code().createConstructorCall(
                uriTypeRef
            );
            uriConstructorCall.setArguments(java.util.Collections.singletonList(uriLiteral));
            
            // Replace the invocation with newInstance(new URI("."))
            invocation.setArguments(java.util.Arrays.asList(uriConstructorCall));
        }
        
        // Write transformed code to output directory
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete! Output written to: " + outputDir);
    }
}