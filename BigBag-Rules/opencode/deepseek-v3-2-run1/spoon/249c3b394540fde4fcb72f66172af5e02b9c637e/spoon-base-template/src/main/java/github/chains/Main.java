package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.AbstractFilter;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtTry;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying snakeyaml 1.31 compatibility transformation to: " + sourceDirectory);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDirectory);
        
        // Create the transformation
        launcher.addProcessor(new RepresenterGetPropertiesTransformation());
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Transformation completed successfully.");
    }
    
    private static class RepresenterGetPropertiesTransformation extends spoon.processing.AbstractProcessor<CtClass<?>> {
        private static final String REPRESENTER_CLASS = "org.yaml.snakeyaml.representer.Representer";
        private static final String SAFE_REPRESENTER_CLASS = "org.yaml.snakeyaml.representer.SafeRepresenter";
        private static final String INTROSPECTION_EXCEPTION = "java.beans.IntrospectionException";
        private static final String GET_PROPERTIES_METHOD = "getProperties";
        
        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            // Check if this class extends Representer or SafeRepresenter
            if (candidate.getSuperclass() == null) {
                return false;
            }
            
            CtTypeReference<?> superClass = candidate.getSuperclass();
            String superClassName = superClass.getQualifiedName();
            
            return REPRESENTER_CLASS.equals(superClassName) || 
                   SAFE_REPRESENTER_CLASS.equals(superClassName) ||
                   extendsRepresenter(superClass);
        }
        
        private boolean extendsRepresenter(CtTypeReference<?> typeRef) {
            if (typeRef == null) {
                return false;
            }
            
            String className = typeRef.getQualifiedName();
            if (REPRESENTER_CLASS.equals(className) || SAFE_REPRESENTER_CLASS.equals(className)) {
                return true;
            }
            
            // Recursively check superclass
            return extendsRepresenter(typeRef.getSuperclass());
        }
        
        @Override
        public void process(CtClass<?> ctClass) {
            System.out.println("Processing class: " + ctClass.getQualifiedName());
            
            // Find all getProperties methods in this class
            Set<CtMethod<?>> methods = ctClass.getMethods();
            for (CtMethod<?> method : methods) {
                if (GET_PROPERTIES_METHOD.equals(method.getSimpleName())) {
                    processGetPropertiesMethod(method);
                }
            }
            
            // Also check for any methods that throw IntrospectionException
            // that might be calling super.getProperties
            for (CtMethod<?> method : methods) {
                Set<CtTypeReference<? extends Throwable>> thrownTypes = method.getThrownTypes();
                if (thrownTypes != null) {
                    for (CtTypeReference<? extends Throwable> thrownType : thrownTypes) {
                        if (INTROSPECTION_EXCEPTION.equals(thrownType.getQualifiedName())) {
                            // Check if this method calls super.getProperties
                            if (callsSuperGetProperties(method)) {
                                // Remove the throws clause for IntrospectionException
                                removeIntrospectionExceptionThrows(method);
                            }
                        }
                    }
                }
            }
            
            // Note: Import removal would need to be done at compilation unit level
            // This is a simplified transformation focusing on the method signatures
        }
        
        private void processGetPropertiesMethod(CtMethod<?> method) {
            System.out.println("  Processing method: " + method.getSignature());
            
            // Remove IntrospectionException from throws clause if present
            removeIntrospectionExceptionThrows(method);
            
            // Also need to handle any try-catch blocks inside the method
            // that catch IntrospectionException
            removeIntrospectionExceptionCatches(method);
        }
        
        private void removeIntrospectionExceptionThrows(CtMethod<?> method) {
            Set<CtTypeReference<? extends Throwable>> thrownTypes = method.getThrownTypes();
            if (thrownTypes == null || thrownTypes.isEmpty()) {
                return;
            }
            
            CtTypeReference<? extends Throwable> toRemove = null;
            
            for (CtTypeReference<? extends Throwable> thrownType : thrownTypes) {
                if (INTROSPECTION_EXCEPTION.equals(thrownType.getQualifiedName())) {
                    toRemove = thrownType;
                    break;
                }
            }
            
            if (toRemove != null) {
                thrownTypes.remove(toRemove);
                System.out.println("    Removed throws IntrospectionException from method: " + method.getSimpleName());
            }
        }
        
        private void removeIntrospectionExceptionCatches(CtMethod<?> method) {
            if (method.getBody() == null) {
                return;
            }
            
            // Find all try blocks in the method
            List<CtTry> tryBlocks = method.getBody().getElements(new AbstractFilter<CtTry>(CtTry.class) {
                @Override
                public boolean matches(CtTry tryBlock) {
                    return true;
                }
            });
            
            for (CtTry tryBlock : tryBlocks) {
                List<CtCatch> catchers = tryBlock.getCatchers();
                for (CtCatch catcher : catchers) {
                    CtTypeReference<?> caughtType = catcher.getParameter().getType();
                    if (caughtType != null && INTROSPECTION_EXCEPTION.equals(caughtType.getQualifiedName())) {
                        // Check if the catch block just rethrows or handles the exception
                        // For now, we'll just remove empty catch blocks
                        if (catcher.getBody().getStatements().isEmpty()) {
                            catchers.remove(catcher);
                            System.out.println("    Removed empty catch block for IntrospectionException");
                            break;
                        }
                    }
                }
            }
        }
        
        private boolean callsSuperGetProperties(CtMethod<?> method) {
            if (method.getBody() == null) {
                return false;
            }
            
            // Look for super.getProperties calls in the method body
            List<spoon.reflect.code.CtInvocation<?>> invocations = 
                method.getBody().getElements(new AbstractFilter<spoon.reflect.code.CtInvocation<?>>(spoon.reflect.code.CtInvocation.class) {
                    @Override
                    public boolean matches(spoon.reflect.code.CtInvocation<?> invocation) {
                        return "getProperties".equals(invocation.getExecutable().getSimpleName()) &&
                               invocation.getTarget() != null &&
                               "super".equals(invocation.getTarget().toString());
                    }
                });
            
            return !invocations.isEmpty();
        }
        
        
    }
}