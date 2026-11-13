package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;

public class Main {
    public static void main(String[] args) {
        // Generic transformation for fixing plexus-container-default breaking change
        // where getLoggerManager() was moved from PlexusContainer to MutablePlexusContainer
        
        // Set up Spoon
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/plexus-archiver/src");
        launcher.setSourceOutputDirectory("/workspace/plexus-archiver/src");
        
        // Build the model
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Find all invocations of getLoggerManager() method
        launcher.getModel().filterChildren(e -> {
            if (e instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) e;
                if ("getLoggerManager".equals(invocation.getExecutable().getSimpleName())) {
                    // Check if the target is a PlexusContainer reference
                    if (invocation.getTarget() != null) {
                        CtTypeReference<?> targetType = invocation.getTarget().getType();
                        if (targetType != null && 
                            targetType.getQualifiedName().contains("PlexusContainer") &&
                            !targetType.getQualifiedName().contains("MutablePlexusContainer")) {
                            
                            // Transform: container.getLoggerManager() 
                            // to: ((MutablePlexusContainer) container).getLoggerManager()
                            
                            // Create a cast to MutablePlexusContainer
                            CtTypeReference<?> mutablePlexusContainerRef = 
                                factory.Type().createReference("org.codehaus.plexus.MutablePlexusContainer");
                            
                            // Create the cast access expression using proper factory method
                            CtTypeAccess<?> castAccess = factory.Code().createTypeAccess(mutablePlexusContainerRef);
                            castAccess.setAccessedType(mutablePlexusContainerRef);
                            castAccess.setTarget(invocation.getTarget());
                            
                            // Replace the invocation target with the cast
                            invocation.setTarget(castAccess);
                        }
                    }
                }
            }
            return false;
        }).list();
    }
}