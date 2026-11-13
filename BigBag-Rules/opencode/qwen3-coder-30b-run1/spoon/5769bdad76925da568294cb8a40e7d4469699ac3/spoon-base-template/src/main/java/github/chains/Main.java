package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Set the source directory
        String sourceDirectory = "/workspace/incrementals-tools";
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory("/tmp/spooned");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Find all field reads to the 'status' field on GHCompare
        List<CtFieldRead> fieldReads = model.getElements(new TypeFilter<>(CtFieldRead.class));
        
        int count = 0;
        for (CtFieldRead fieldRead : fieldReads) {
            if (fieldRead.getVariable() != null && 
                fieldRead.getVariable().getSimpleName().equals("status")) {
                
                // Check if this is accessing GHCompare.status field
                if (fieldRead.getTarget() != null && 
                    fieldRead.getTarget().getType() != null &&
                    fieldRead.getTarget().getType().getQualifiedName().equals("org.kohsuke.github.GHCompare")) {
                    
                    // Create a method call to getStatus() instead
                    Factory factory = fieldRead.getFactory();
                    
                    // Get the method reference for getStatus()
                    CtMethod getStatusMethod = factory.Type().createReference("org.kohsuke.github.GHCompare")
                        .getDeclaredMethod("getStatus");
                    
                    // Create the invocation
                    CtInvocation<?> getStatusCall = factory.Code().createInvocation(
                        fieldRead.getTarget(), 
                        getStatusMethod.getExecutableReference()
                    );
                    
                    // Replace the field read with the method call
                    fieldRead.replace(getStatusCall);
                    count++;
                }
            }
        }
        
        System.out.println("Transformed " + count + " instances of GHCompare.status access");
        
        // Save the transformed code
        launcher.process();
        
        // Print the result
        System.out.println("Transformation completed on " + sourceDirectory);
    }
}