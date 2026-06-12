package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;

public class Main {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java -jar spoon-transform.jar <input-source-dir> <output-dir> <target-class> <method-name> <field-name>");
            System.err.println("  input-source-dir: Path to the source code directory to transform");
            System.err.println("  output-dir: Path where transformed code will be written");
            System.err.println("  target-class: Fully qualified class name (e.g., org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy)");
            System.err.println("  method-name: Method to replace (e.g., getInstance)");
            System.err.println("  field-name: Field to use as replacement (e.g., INSTANCE)");
            System.err.println("");
            System.err.println("Example for jaxb2-basics-runtime breaking change:");
            System.err.println("  java -jar spoon-transform.jar ./src ./out org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy getInstance INSTANCE");
            System.exit(1);
        }

        String inputSourceDir = args[0];
        String outputDir = args[1];
        String targetClass = args[2];
        String methodName = args[3];
        String fieldName = args[4];
        
        System.out.println("Transforming: " + inputSourceDir);
        System.out.println("Output to: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(inputSourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add transformation with parameters
        launcher.addProcessor(new GetInstanceToInstanceFieldTransformer(targetClass, methodName, fieldName));
        
        // Run transformation
        launcher.run();
        
        System.out.println("Transformation complete!");
    }
    
    /**
     * Spoon processor that transforms method calls to field accesses.
     * This handles breaking API changes where a static method is replaced with a static field.
     * Example: JAXBToStringStrategy.getInstance() -> JAXBToStringStrategy.INSTANCE
     * 
     * The transformation is generic and can handle any class that follows this pattern.
     */
    static class GetInstanceToInstanceFieldTransformer extends AbstractProcessor<CtInvocation<?>> {
        
        private final String targetClass;
        private final String methodName;
        private final String fieldName;
        
        public GetInstanceToInstanceFieldTransformer(String targetClass, String methodName, String fieldName) {
            this.targetClass = targetClass;
            this.methodName = methodName;
            this.fieldName = fieldName;
        }
        
        @Override
        public void process(CtInvocation<?> invocation) {
            // Check if this is the target method call with no arguments
            if (!methodName.equals(invocation.getExecutable().getSimpleName()) 
                || !invocation.getArguments().isEmpty()) {
                return;
            }
            
            Factory factory = getFactory();
            
            // Get the target expression
            var target = invocation.getTarget();
            if (target == null) {
                return;
            }
            
            // Get the target type to check if it matches the target class
            var targetType = target.getType();
            if (targetType == null) {
                System.out.println("DEBUG: targetType is null");
                return;
            }
            
            // Check if this is a call on the target class
            String targetTypeName = targetType.getQualifiedName();
            System.out.println("DEBUG: Checking target type: " + targetTypeName + " against: " + targetClass);
            if (!targetClass.equals(targetTypeName)) {
                System.out.println("DEBUG: Type mismatch, skipping");
                return;
            }
            
            System.out.println("Processing: " + target + "." + methodName + "()");
            
            // Create a simple field access using the factory's createFieldRead method
            // Create field read directly
            CtFieldRead<?> fieldRead = factory.createFieldRead();
            
            // Set the target (clone it)
            fieldRead.setTarget(target.clone());
            
            // Create a field reference
            var fieldRef = factory.createFieldReference();
            fieldRef.setSimpleName(fieldName);
            
            // Try to set the type from the invocation's return type
            var returnType = invocation.getType();
            if (returnType != null) {
                fieldRef.setType(returnType.clone());
                fieldRef.setDeclaringType(returnType.clone());
            }
            
            // Use reflection to set the variable since the type system is being difficult
            try {
                java.lang.reflect.Method setVariableMethod = CtFieldRead.class.getMethod("setVariable", spoon.reflect.reference.CtVariableReference.class);
                setVariableMethod.invoke(fieldRead, fieldRef);
            } catch (Exception e) {
                System.err.println("Failed to set field reference: " + e.getMessage());
                return;
            }
            
            // Replace the method call with field access
            invocation.replace(fieldRead);
            
            System.out.println("Successfully transformed: " + target + "." + methodName + "() -> " + target + "." + fieldName);
        }
    }
}