package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtFieldReadImpl;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.err.println("Example: java github.chains.Main /path/to/project /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying transformation: Replace GHCompare.status field access with getStatus() method call");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Add processor to transform field accesses to method calls
            launcher.addProcessor(new FieldToMethodProcessor());
            
            // Run the transformation
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Generic processor that transforms field accesses to method calls.
     * This handles the pattern where a public field becomes private and is replaced by a getter method.
     */
    static class FieldToMethodProcessor extends AbstractProcessor<CtFieldRead<?>> {
        
        // Configuration: Field to transform and its corresponding getter method
        private static final String TARGET_CLASS = "org.kohsuke.github.GHCompare";
        private static final String TARGET_FIELD = "status";
        private static final String GETTER_METHOD = "getStatus";
        
        @Override
        public void process(CtFieldRead<?> fieldRead) {
            CtFieldReference<?> fieldRef = fieldRead.getVariable();
            
            // Check if this is the field we want to transform
            if (isTargetField(fieldRef)) {
                System.out.println("Found field access to transform: " + fieldRef.getQualifiedName());
                System.out.println("  Field simple name: " + fieldRef.getSimpleName());
                System.out.println("  Field declaring type: " + fieldRef.getDeclaringType());
                System.out.println("  Field parent: " + fieldRead.getParent());
                System.out.println("  Has target? " + (fieldRead.getTarget() != null));
                if (fieldRead.getTarget() != null) {
                    System.out.println("  Target type: " + fieldRead.getTarget().getType());
                    System.out.println("  Target: " + fieldRead.getTarget());
                }
                
                // Get the target expression (the object on which the field is accessed)
                CtExpression<?> target = fieldRead.getTarget();
                if (target == null) {
                    // Static field access - shouldn't happen for instance fields, but handle gracefully
                    System.err.println("Warning: Static field access found for " + fieldRef.getQualifiedName());
                    return;
                }
                
                // Create executable reference for the getter method
                // The signature is: createReference(declaringType, isConstructor, returnType, methodName, parameterTypes...)
                CtExecutableReference<?> execRef = getFactory().Executable().createReference(
                    getFactory().Type().createReference(TARGET_CLASS),  // declaring type
                    false,  // isConstructor
                    getFactory().Type().createReference("org.kohsuke.github.GHCompare$Status"),  // return type
                    GETTER_METHOD  // method name
                    // No parameter types for getter
                );
                
                // Create a method invocation to replace the field access
                CtInvocation<?> methodInvocation = getFactory().Code().createInvocation(
                    target.clone(),
                    execRef
                );
                
                // Replace the field read with the method invocation
                fieldRead.replace(methodInvocation);
                
                System.out.println("Transformed field access to method call: " + 
                    target.toString() + "." + TARGET_FIELD + " -> " + 
                    target.toString() + "." + GETTER_METHOD + "()");
            }
        }
        
        private boolean isTargetField(CtFieldReference<?> fieldRef) {
            if (fieldRef == null) {
                return false;
            }
            
            // Check field name - this is the primary criteria
            if (!TARGET_FIELD.equals(fieldRef.getSimpleName())) {
                return false;
            }
            
            // If we have declaring type information, check it matches
            CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Check if it matches our target class
                if (TARGET_CLASS.equals(qualifiedName)) {
                    return true;
                }
                // Also check if it's the enum type (GHCompare.Status) which might appear in some contexts
                if ("org.kohsuke.github.GHCompare$Status".equals(qualifiedName) || 
                    "org.kohsuke.github.GHCompare.Status".equals(qualifiedName)) {
                    // This would be accessing GHCompare.Status.someEnumValue, not GHCompare.status field
                    return false;
                }
            }
            
            // If declaring type is null, we still want to process it
            // This handles cases where Spoon can't resolve the type without full classpath
            return true;
        }
        
        @Override
        public boolean isToBeProcessed(CtFieldRead<?> element) {
            CtFieldReference<?> fieldRef = element.getVariable();
            boolean shouldProcess = fieldRef != null && isTargetField(fieldRef);
            if (shouldProcess) {
                System.out.println("isToBeProcessed: TRUE for field: " + fieldRef.getSimpleName());
            }
            return shouldProcess;
        }
    }
}