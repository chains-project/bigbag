package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation to fix the breaking API change in maven-dependency-tree 3.1.0
 * where DependencyGraphBuilder.buildDependencyGraph method signature changed from:
 * buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter) to
 * buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter, Collection)
 * 
 * This transformation adds the missing third parameter (empty Collection) to all calls
 * to buildDependencyGraph that have the old 2-parameter signature.
 */
public class Main {
    public static void main(String[] args) {
        // Input directory for the source code to transform
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/license-maven-plugin";
        
        System.out.println("Analyzing source directory: " + sourceDirectory);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/transformed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all invocations of buildDependencyGraph method with the old signature
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                // Looking for calls to buildDependencyGraph method on DependencyGraphBuilder
                CtExecutableReference<?> executableRef = element.getExecutable();
                if (executableRef == null) return false;
                
                // Check method name
                String methodName = executableRef.getSimpleName();
                if (!"buildDependencyGraph".equals(methodName)) return false;
                
                // Check if it's the old method signature (2 parameters)
                // The old signature was: buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter)
                // The new signature is: buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter, Collection)
                return executableRef.getParameters().size() == 2;
            }
        });
        
        System.out.println("Found " + invocations.size() + " calls to buildDependencyGraph with old signature");
        
        Factory factory = launcher.getFactory();
        
        // For each invocation, we'll add a third empty collection argument
        for (CtInvocation<?> invocation : invocations) {
            // Get the method declaration to understand its context
            CtMethod<?> method = invocation.getParent(CtMethod.class);
            
            if (method != null) {
                System.out.println("Fixing buildDependencyGraph call in method: " + method.getSignature());
                
                // Add a third argument - empty collection
                // We'll need to add Collections.emptyList() as the third argument
                CtLiteral<?> emptyList = factory.Code().createLiteral(java.util.Collections.EMPTY_LIST);
                invocation.addArgument(emptyList);
            }
        }
        
        System.out.println("Transformations prepared. Writing output...");
        // Write the modified files
        launcher.setSourceOutputDirectory(sourceDirectory + "/transformed");
        launcher.process();
        System.out.println("Transformation completed. Check transformed/ directory.");
    }
}