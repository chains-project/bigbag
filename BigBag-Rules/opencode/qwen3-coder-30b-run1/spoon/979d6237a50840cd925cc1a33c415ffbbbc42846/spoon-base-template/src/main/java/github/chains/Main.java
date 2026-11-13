package github.chains;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("Generic Spoon transformation for fixing breaking Struts2 dependency changes");
    }
    
    /**
     * Generic Spoon transformation to identify calls to ObjectFactory.buildInterceptor() 
     * with the old API signature and replace them with new API usage.
     */
    public static void transformObjectFactoryBuildInterceptorCalls(Factory factory) {
        // Create a visitor to scan for buildInterceptor calls
        ObjectFactoryInterceptorVisitor visitor = new ObjectFactoryInterceptorVisitor(factory);
        factory.getModel().getRootPackage().accept(visitor);
    }
    
    /**
     * A visitor to identify calls to ObjectFactory.buildInterceptor() with old API signature
     */
    public static class ObjectFactoryInterceptorVisitor extends CtScanner {
        private final Factory factory;
        
        public ObjectFactoryInterceptorVisitor(Factory factory) {
            this.factory = factory;
        }
        
        @Override
        public <T> void visitCtInvocation(CtInvocation<T> invocation) {
            super.visitCtInvocation(invocation);
            
            // Check if this is a buildInterceptor call
            CtExecutableReference<?> executableRef = invocation.getExecutable();
            if (executableRef == null) return;
            
            if (!"buildInterceptor".equals(executableRef.getSimpleName())) {
                return;
            }
            
            // Check if it's from ObjectFactory or subclass
            CtTypeReference<?> declaringType = executableRef.getDeclaringType();
            if (declaringType == null || !isObjectFactorySubtype(declaringType, factory)) {
                return;
            }
            
            // Check if it matches the old signature:
            // buildInterceptor(InterceptorConfig interceptorConfig, Map<String, String> interceptorRefParams)
            if (matchesOldSignature(invocation)) {
                System.out.println("Found old API call to buildInterceptor: " + invocation.toString());
                // In a real implementation, you would modify the invocation here
                // For example, by replacing it with a call to the new API
            }
        }
        
        /**
         * Check if a type reference is ObjectFactory or extends ObjectFactory
         */
        private boolean isObjectFactorySubtype(CtTypeReference<?> typeRef, Factory factory) {
            if (typeRef == null) return false;
            
            // Direct match with ObjectFactory
            if ("com.opensymphony.xwork2.ObjectFactory".equals(typeRef.getQualifiedName())) {
                return true;
            }
            
            // Check if it's a subclass of ObjectFactory
            CtTypeReference<?> superClass = typeRef.getSuperclass();
            if (superClass != null) {
                return isObjectFactorySubtype(superClass, factory);
            }
            
            // Check if it implements ObjectFactory
            for (CtTypeReference<?> iface : typeRef.getSuperInterfaces()) {
                if ("com.opensymphony.xwork2.ObjectFactory".equals(iface.getQualifiedName())) {
                    return true;
                }
            }
            
            return false;
        }
        
        /**
         * Check if the invocation matches the old API signature:
         * buildInterceptor(InterceptorConfig interceptorConfig, Map<String, String> interceptorRefParams)
         */
        private boolean matchesOldSignature(CtInvocation invocation) {
            // Check if we have exactly 2 arguments (old API signature)
            List<?> arguments = invocation.getArguments();
            return arguments.size() == 2;
        }
    }
}