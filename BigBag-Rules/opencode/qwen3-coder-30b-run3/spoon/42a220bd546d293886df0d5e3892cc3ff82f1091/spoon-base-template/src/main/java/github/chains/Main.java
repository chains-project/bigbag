package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.Arrays;
import java.util.List;

/**
 * Generic transformation rule to migrate from geoip2 v2.x to v3.0.0
 * Specifically handles the breaking change where methods return Optional instead of throwing exceptions
 */
public class Main {
    public static void main(String[] args) {
        // Parse the input source code
        Launcher launcher = new Launcher();
        
        // Set the input directory to transform
        String inputPath = "/workspace/minfraud-api-java/src/main/java";
        launcher.addInputResource(inputPath);
        
        // Set the output directory for transformed code
        String outputPath = "/workspace/minfraud-api-java-transformed/src/main/java";
        launcher.setSourceOutputDirectory(outputPath);
        
        // Add our processor to transform the code
        launcher.addProcessor(new GeoIp2MigrationProcessor());
        
        // Build the model
        launcher.buildModel();
        
        // Process the model
        launcher.process();
        
        // Print completion message
        System.out.println("Transformation complete! Check " + outputPath);
    }
    
    /**
     * Processor that transforms geoip2 v2.x API calls to v3.0.0 compatible calls
     */
    public static class GeoIp2MigrationProcessor extends AbstractProcessor<CtInvocation> {
        
        // Methods that were changed from throwing exceptions to returning Optional
        private static final List<String> METHODS_THAT_CHANGED = Arrays.asList(
            "country", "city", "asn", "isp", "domain", 
            "connectionType", "anonymousIp", "enterprise"
        );
        
        @Override
        public void process(CtInvocation invocation) {
            // Skip if not a method call or target is null
            if (invocation.getTarget() == null) {
                return;
            }
            
            // Get the method executable reference
            CtExecutableReference<?> executableRef = invocation.getExecutable();
            if (executableRef == null) {
                return;
            }
            
            // Get the method name
            String methodName = executableRef.getSimpleName();
            
            // Check if this is one of the methods that changed
            if (!METHODS_THAT_CHANGED.contains(methodName)) {
                return;
            }
            
            // Get the target type
            CtTypeReference<?> targetType = invocation.getTarget().getType();
            if (targetType == null) {
                return;
            }
            
            // Check if this is a GeoIP2 provider type
            if (isGeoIp2Provider(targetType)) {
                // Replace with try* method
                replaceWithTryMethod(invocation, methodName);
            }
        }
        
        /**
         * Check if the type represents a GeoIP2 provider interface/class
         */
        private boolean isGeoIp2Provider(CtTypeReference<?> typeRef) {
            if (typeRef == null) {
                return false;
            }
            
            String typeName = typeRef.getQualifiedName();
            return typeName.contains("geoip2") && (
                typeName.contains("DatabaseProvider") ||
                typeName.contains("GeoIp2Provider") ||
                typeName.contains("DatabaseReader") ||
                typeName.contains("WebServiceClient")
            );
        }
        
        /**
         * Replace a regular method call with optional-based try* method call
         */
        private void replaceWithTryMethod(CtInvocation invocation, String originalMethodName) {
            // Create the new method name (try variant)
            String newMethodName = "try" + capitalizeFirst(originalMethodName);
            
            // Replace the method name in the executable reference
            CtExecutableReference<?> executableRef = invocation.getExecutable();
            if (executableRef != null) {
                executableRef.setSimpleName(newMethodName);
            }
        }
        
        /**
         * Helper method to capitalize first letter of a string
         */
        private String capitalizeFirst(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }
}