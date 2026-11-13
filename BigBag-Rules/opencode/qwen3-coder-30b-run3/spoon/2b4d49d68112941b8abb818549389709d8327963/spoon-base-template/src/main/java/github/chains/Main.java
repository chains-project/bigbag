package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation for fixing Hazelcast breaking dependency updates.
 * This transformation targets common breaking changes in Hazelcast aggregation APIs
 * where method signatures have changed (e.g., adding attribute path parameter).
 */
public class Main {
    
    public static void main(String[] args) {
        // Default to the current directory or accept a path as argument
        String sourcePath = args.length > 0 ? args[0] : "/workspace/openfire-hazelcast-plugin";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        launcher.setSourceOutputDirectory("/tmp/spoon-generated");
        
        // Set the JVM version
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Find all invocations of Hazelcast Aggregators methods that might have changed
        // Looking for calls to static methods in com.hazelcast.aggregation.Aggregators
        model.getElements(new TypeFilter<>(CtInvocation.class))
            .stream()
            .filter(invocation -> {
                // Check if it's a call to a method in the Aggregators class
                CtExecutableReference<?> executableRef = invocation.getExecutable();
                if (executableRef == null) return false;
                
                CtTypeReference<?> declaringType = executableRef.getDeclaringType();
                if (declaringType == null) return false;
                
                return declaringType.getQualifiedName().startsWith("com.hazelcast.aggregation.Aggregators");
            })
            .forEach(invocation -> {
                try {
                    // Handle the breaking change: Adding attribute path parameter to aggregation methods
                    handleHazelcastAggregationBreakingChange(invocation);
                } catch (Exception e) {
                    System.err.println("Error processing invocation: " + e.getMessage());
                }
            });
        
        // Write the modified code
        launcher.setSourceOutputDirectory("/tmp/spoon-generated");
        launcher.process();
        
        System.out.println("Transformation completed. Processed files written to /tmp/spoon-generated");
    }
    
    /**
     * Handle common Hazelcast breaking changes in aggregation APIs
     * Specifically targeting the addition of attribute path parameter to methods
     */
    private static void handleHazelcastAggregationBreakingChange(CtInvocation<?> invocation) {
        // Get the invoked method
        CtExecutableReference<?> executableRef = invocation.getExecutable();
        String methodName = executableRef.getSimpleName();
        
        // Check if we need to add an attribute path parameter (typically "")
        // This is a generic approach for handling breaking changes in aggregation methods
        if (isAggregationMethod(methodName)) {
            // Check if the method expects an attribute path parameter
            if (invocation.getArguments().size() == 0) {
                // Add empty string as attribute path parameter for compatibility
                invocation.addArgument(
                    invocation.getFactory().createLiteral("")
                );
            }
        }
    }
    
    private static boolean isAggregationMethod(String methodName) {
        // Common aggregation method names that might have changed
        return "count".equals(methodName) || 
               "sum".equals(methodName) ||
               "avg".equals(methodName) ||
               "min".equals(methodName) ||
               "max".equals(methodName) ||
               "distinct".equals(methodName);
    }
}