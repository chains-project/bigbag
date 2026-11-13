package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;
import java.util.Set;

/**
 * Generic Spoon transformation rule for fixing Hibernate UserType API breaking change.
 * 
 * Breaking Change: Hibernate 5.x changed UserType interface method signatures:
 * - Old: nullSafeGet(ResultSet, String[], SessionImplementor, Object)
 * - New: nullSafeGet(ResultSet, String[], SharedSessionContractImplementor, Object)
 * - Same change for nullSafeSet method
 * 
 * This transformation:
 * 1. Finds all classes implementing org.hibernate.usertype.UserType
 * 2. Updates method signatures of nullSafeGet and nullSafeSet
 * 3. Updates method parameter types in implementations
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        
        // Add our processor
        launcher.addProcessor(new HibernateUserTypeFixProcessor());
        
        // Run Spoon
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Processor that fixes Hibernate UserType API breaking changes
     */
    public static class HibernateUserTypeFixProcessor extends AbstractProcessor<CtClass<?>> {
        
        // Target interface that we're looking for
        private static final String USER_TYPE_INTERFACE = "org.hibernate.usertype.UserType";
        
        // Old type to replace
        private static final String OLD_TYPE = "org.hibernate.engine.spi.SessionImplementor";
        private static final String OLD_TYPE_SIMPLE = "SessionImplementor";
        
        // New type to use
        private static final String NEW_TYPE = "org.hibernate.engine.spi.SharedSessionContractImplementor";
        private static final String NEW_TYPE_SIMPLE = "SharedSessionContractImplementor";
        
        // Methods to fix
        private static final String[] METHODS_TO_FIX = {"nullSafeGet", "nullSafeSet"};
        
        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            // Check if this class implements org.hibernate.usertype.UserType
            Set<CtTypeReference<?>> interfaces = candidate.getSuperInterfaces();
            for (CtTypeReference<?> iface : interfaces) {
                if (iface.getQualifiedName().equals(USER_TYPE_INTERFACE)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public void process(CtClass<?> ctClass) {
            System.out.println("Processing class: " + ctClass.getQualifiedName());
            
            // Fix method signatures
            fixMethodSignatures(ctClass);
            
            System.out.println("Successfully fixed: " + ctClass.getQualifiedName());
        }
        
        private void fixMethodSignatures(CtClass<?> ctClass) {
            // Find and fix all methods that need updating
            for (CtMethod<?> method : ctClass.getMethods()) {
                if (shouldFixMethod(method)) {
                    fixMethodParameters(method);
                }
            }
        }
        
        private boolean shouldFixMethod(CtMethod<?> method) {
            String methodName = method.getSimpleName();
            
            // Check if this is one of the methods we need to fix
            for (String targetMethod : METHODS_TO_FIX) {
                if (methodName.equals(targetMethod)) {
                    return true;
                }
            }
            return false;
        }
        
        private void fixMethodParameters(CtMethod<?> method) {
            System.out.println("  Fixing method: " + method.getSignature());
            
            List<CtParameter<?>> parameters = method.getParameters();
            
            // The third parameter (index 2) is the one that needs to be changed
            if (parameters.size() >= 3) {
                CtParameter<?> param = parameters.get(2);
                CtTypeReference<?> paramType = param.getType();
                
                // Check if this parameter has the old type (fully qualified or simple name)
                String paramTypeName = paramType.getQualifiedName();
                if (paramTypeName.equals(OLD_TYPE) || paramTypeName.equals(OLD_TYPE_SIMPLE)) {
                    // Replace the parameter type with the new type
                    CtTypeReference<?> newType = getFactory().Type().createReference(NEW_TYPE);
                    param.setType(newType);
                    System.out.println("    Updated parameter " + param.getSimpleName() + 
                                     " from " + paramTypeName + " to " + NEW_TYPE_SIMPLE);
                }
            }
        }
    }
}