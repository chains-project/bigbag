package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix GitHub API breaking changes in org.kohsuke.github.GHCompare.status access.
 * This fixes the issue where GHCompare.status field was changed from public to private in github-api 1.313.
 */
public class Main {
    public static void main(String[] args) {
        // Process the source directory provided as argument
        String sourceDir = args.length > 0 ? args[0] : "/workspace/incrementals-tools";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory("/tmp/spoon-transform-output");
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Find all CtFieldAccess elements (field access expressions)
        List<CtFieldAccess> fieldAccesses = model.getElements(new TypeFilter<>(CtFieldAccess.class));
        
        for (CtFieldAccess fieldAccess : fieldAccesses) {
            // Check if this is accessing the status field
            if (fieldAccess.getVariable() != null) {
                String fieldName = fieldAccess.getVariable().getSimpleName();
                if ("status".equals(fieldName)) {
                    // Check if this is a field access on a GHCompare.getCompare() invocation
                    if (fieldAccess.getTarget() instanceof CtInvocation) {
                        CtInvocation invocation = (CtInvocation) fieldAccess.getTarget();
                        
                        // Verify that we're getting the "getCompare" method
                        if (invocation.getExecutable() != null) {
                            String methodName = invocation.getExecutable().getSimpleName();
                            if ("getCompare".equals(methodName)) {
                                // Create a new invocation for getStatus() with the same arguments
                                CtInvocation newInvocation = invocation.clone();
                                newInvocation.getExecutable().setSimpleName("getStatus");
                                
                                // Replace the field access with the method call
                                fieldAccess.replace(newInvocation);
                            }
                        }
                    }
                }
            }
        }
        
        // Process and output the changes
        launcher.process();
        System.out.println("Transformation completed successfully!");
    }
}