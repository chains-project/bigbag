package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/project /path/to/transformed");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        String outputPath = args[1];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        launcher.setSourceOutputDirectory(outputPath);
        
        // Add processor for field-to-getter transformation
        launcher.addProcessor(new FieldToGetterProcessor(
            "org.kohsuke.github.GHCompare", 
            "status", 
            "getStatus"
        ));
        
        launcher.run();
        System.out.println("Transformation complete. Output written to: " + outputPath);
    }
    
    /**
     * Generic processor that transforms field accesses to getter method calls.
     * This can handle any field-to-getter migration in any class.
     */
    public static class FieldToGetterProcessor extends AbstractProcessor<CtFieldAccess<?>> {
        private final String targetClassName;
        private final String fieldName;
        private final String getterMethodName;
        
        public FieldToGetterProcessor(String targetClassName, String fieldName, String getterMethodName) {
            this.targetClassName = targetClassName;
            this.fieldName = fieldName;
            this.getterMethodName = getterMethodName;
        }
        
        @Override
        public boolean isToBeProcessed(CtFieldAccess<?> fieldAccess) {
            CtFieldReference<?> fieldRef = fieldAccess.getVariable();
            if (fieldRef == null) {
                return false;
            }
            
            // Check if this is a field access to our target field
            if (!fieldName.equals(fieldRef.getSimpleName())) {
                return false;
            }
            
            // Get the declaring type of the field
            CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
            if (declaringType == null) {
                return false;
            }
            
            // Check if the declaring type matches our target class
            String qualifiedName = declaringType.getQualifiedName();
            return targetClassName.equals(qualifiedName);
        }
        
        @Override
        public void process(CtFieldAccess<?> fieldAccess) {
            try {
                // Create method call: target.getStatus()
                CtExpression<?> target = fieldAccess.getTarget();
                
                // Create method reference
                CtTypeReference<?> declaringType = fieldAccess.getVariable().getDeclaringType();
                CtTypeReference<?> returnType = fieldAccess.getType();
                
                // Use createExecutableReference with proper casting
                CtExecutableReference methodRef = getFactory().Core().createExecutableReference();
                methodRef.setDeclaringType(declaringType);
                methodRef.setSimpleName(getterMethodName);
                methodRef.setType(returnType);
                
                // Create invocation
                CtInvocation invocation = getFactory().Core().createInvocation();
                invocation.setTarget(target.clone());
                invocation.setExecutable(methodRef);
                
                // Replace field access with method invocation
                fieldAccess.replace((CtExpression) invocation);
                
                System.out.println("Transformed field access to getter: " + 
                    targetClassName + "." + fieldName + " -> " + 
                    targetClassName + "." + getterMethodName + "()");
            } catch (Exception e) {
                System.err.println("Error transforming field access: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}