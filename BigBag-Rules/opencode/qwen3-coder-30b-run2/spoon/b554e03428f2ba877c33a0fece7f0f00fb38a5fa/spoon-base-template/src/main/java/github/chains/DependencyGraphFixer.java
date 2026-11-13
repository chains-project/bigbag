package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class DependencyGraphFixer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java DependencyGraphFixer <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDir);
        launcher.buildModel();
        
        // Find all invocations of buildDependencyGraph with 2 arguments
        CtModel model = launcher.getModel();
        
        // Look for all methods with the old signature of buildDependencyGraph (2 parameters)  
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                CtExecutableReference<?> execRef = element.getExecutable();
                
                // Check if it's a call to buildDependencyGraph
                if (execRef.getSimpleName().equals("buildDependencyGraph")) {
                    // Check if it has exactly 2 parameters (the old signature)
                    // We're looking for org.apache.maven.shared.dependency.graph.DependencyGraphBuilder.buildDependencyGraph
                    return execRef.getParameters().size() == 2 &&
                           execRef.getDeclaringType().getQualifiedName().contains("DependencyGraphBuilder");
                }
                return false;
            }
        });
        
        System.out.println("Found " + invocations.size() + " invocations of buildDependencyGraph with 2 parameters");
        
        // Transform each invocation to use the new signature (adding empty collection)
        for (CtInvocation<?> invocation : invocations) {
            // Create an empty collection for the third parameter
            // The third parameter is java.util.Collection<? extends Artifact>
            CtNewArray<?> emptyCollection = invocation.getFactory().Core().createNewArray();
            emptyCollection.setType(invocation.getFactory().Type().createReference("java.util.Collection"));
            
            // Add the third parameter (empty collection) to the invocation
            List<CtExpression<?>> arguments = invocation.getArguments();
            arguments.add(emptyCollection);
            
            System.out.println("Transformed invocation: " + invocation.toString());
        }
        
        // Print message about what we've done
        System.out.println("Transformation complete. Updated " + invocations.size() + " invocations.");
        
        // Save the modified model back to disk
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.process();
    }
}