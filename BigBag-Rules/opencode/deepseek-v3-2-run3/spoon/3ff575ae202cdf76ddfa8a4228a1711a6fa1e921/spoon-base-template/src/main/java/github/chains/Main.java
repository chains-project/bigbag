package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Main {
    // Methods that were moved from PlexusContainer to MutablePlexusContainer
    private static final Set<String> MOVED_METHODS = new HashSet<>(Arrays.asList(
        "getClassWorld",
        "getComponentDiscovererManager",
        "getComponentFactoryManager",
        "getComponentRealm",
        "getComponentRegistry",
        "getConfiguration",
        "getConfigurationSource",
        "getLogger",
        "getLoggerManager",
        "setComponentDiscovererManager",
        "setComponentFactoryManager",
        "setComponentRegistry",
        "setConfiguration",
        "setConfigurationSource",
        "setLoggerManager"
    ));
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        CtModel model = launcher.buildModel();
        
        // Find all method invocations on PlexusContainer references for methods that moved
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef == null || invocation.getTarget() == null) {
                    return false;
                }
                
                // Check if this is one of the moved methods
                String methodName = execRef.getSimpleName();
                if (!MOVED_METHODS.contains(methodName)) {
                    return false;
                }
                
                // Check if the target expression has type PlexusContainer
                CtTypeReference<?> targetType = invocation.getTarget().getType();
                if (targetType == null) {
                    return false;
                }
                
                // Check if target type is PlexusContainer or assignable to it
                String targetTypeName = targetType.getQualifiedName();
                return "org.codehaus.plexus.PlexusContainer".equals(targetTypeName) ||
                       targetType.isSubtypeOf(launcher.getFactory().Type()
                           .createReference("org.codehaus.plexus.PlexusContainer"));
            }
        });
        
        // Apply transformation: wrap target with cast to MutablePlexusContainer
        for (CtInvocation<?> invocation : invocations) {
            // Create cast expression: (MutablePlexusContainer) target
            CtTypeReference<?> mutablePlexusContainerRef = launcher.getFactory().Type()
                .createReference("org.codehaus.plexus.MutablePlexusContainer");
            
            // Get the target expression
            CtExpression<?> target = invocation.getTarget();
            
            // Add type cast to target expression
            target.addTypeCast(mutablePlexusContainerRef);
        }
        
        // Save transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Applied " + invocations.size() + " fixes.");
    }
}