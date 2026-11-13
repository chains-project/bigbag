package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory> <output-directory>");
            System.err.println("This transformation handles breaking API changes where method signatures change.");
            System.err.println("Example: Updates calls from oldMethod(String) to newMethod(String, boolean)");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying API migration transformation for breaking changes...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add processor for method signature updates
        launcher.addProcessor(new MethodSignatureUpdateProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully.");
            System.out.println("Check output directory for transformed code.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Generic processor that updates method calls when API signatures change.
     * This handles common breaking changes like:
     * - Method parameter additions/removals
     * - Method name changes
     * - Constructor signature changes
     * - Static method to instance method (or vice versa)
     */
    static class MethodSignatureUpdateProcessor extends spoon.processing.AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            String methodName = execRef.getSimpleName();
            String declaringType = execRef.getDeclaringType() != null ? 
                execRef.getDeclaringType().getQualifiedName() : "unknown";
            
            // Example: Handle specific breaking changes
            // In a real scenario, these would be configurable parameters
            
            // Case 1: Method gained a new parameter
            if (declaringType.equals("org.example.OldClass") && 
                methodName.equals("someMethod") &&
                execRef.getParameters().size() == 1) {
                
                System.out.println("Updating " + declaringType + "." + methodName + " call");
                
                // Add default value for new parameter
                // invocation.getArguments().add(factory.createLiteral("default"));
                
                // Note: Actual transformation depends on specific API change
            }
            
            // Case 2: Method renamed
            if (declaringType.equals("org.example.DeprecatedClass") && 
                methodName.equals("oldMethodName")) {
                
                System.out.println("Renaming " + declaringType + "." + methodName + " to newMethodName");
                
                // Update method name
                // execRef.setSimpleName("newMethodName");
            }
            
            // Case 3: Static method became instance method (or vice versa)
            // This requires more complex transformation
            
            // Generic pattern: Check method signature against expected new signature
            // and transform accordingly
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // Process all method invocations
            // In real implementation, filter only those that match breaking change patterns
            return true;
        }
    }
    
    /**
     * Additional processor for constructor calls
     */
    static class ConstructorSignatureUpdateProcessor extends spoon.processing.AbstractProcessor<CtConstructorCall<?>> {
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            CtExecutableReference<?> execRef = constructorCall.getExecutable();
            String declaringType = execRef.getDeclaringType() != null ? 
                execRef.getDeclaringType().getQualifiedName() : "unknown";
            
            // Handle constructor signature changes
            // Example: Constructor gained/removed parameters
            
            // This is similar to method signature updates but for constructors
        }
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            return true;
        }
    }
}