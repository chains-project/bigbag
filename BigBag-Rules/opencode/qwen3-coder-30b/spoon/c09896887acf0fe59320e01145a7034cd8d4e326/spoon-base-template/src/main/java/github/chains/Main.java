package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in github-api dependency.
 * Specifically addresses the change from accessing GHCompare.status field directly
 * to using GHCompare.getStatus() method in version 1.314.
 * 
 * This transformation demonstrates how to:
 * 1. Identify field access patterns in code
 * 2. Replace them with appropriate method calls
 * 3. Make it reusable across projects
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all field accesses to "status" in GHCompare class
        List<CtFieldAccess<?>> fieldAccesses = model.getElements(new TypeFilter<>(CtFieldAccess.class) {
            @Override
            public boolean matches(CtFieldAccess<?> element) {
                // Check if accessing "status" field
                if (element.getVariable() != null && "status".equals(element.getVariable().getSimpleName())) {
                    // Check if the accessed type is GHCompare
                    if (element.getTarget() != null && element.getTarget().getType() != null) {
                        String targetTypeName = element.getTarget().getType().getQualifiedName();
                        if (targetTypeName != null && targetTypeName.equals("org.kohsuke.github.GHCompare")) {
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        
        System.out.println("Found " + fieldAccesses.size() + " field accesses to GHCompare.status");
        
        // Transform each field access to use the getter method
        for (CtFieldAccess<?> fieldAccess : fieldAccesses) {
            // Create a method invocation: ghCompare.getStatus()
            CtInvocation<?> invocation = fieldAccess.getFactory().createInvocation(
                fieldAccess.getTarget(),
                fieldAccess.getFactory().createReference("getStatus")
            );
            
            // Replace the field access with the method invocation
            fieldAccess.replace(invocation);
        }
        
        // Write the transformed code back
        launcher.process();
        
        System.out.println("Transformation completed. Files saved to: " + sourceDirectory + "-transformed");
    }
}