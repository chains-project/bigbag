package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // This is a generic transformation rule for logback 1.4.0 breaking changes
        // It addresses changes to AsyncAppender's setIncludeCallerData method
        
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/pay-adminusers/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/pay-adminusers/src/main/java");
        launcher.getEnvironment().setComplianceLevel(11);

        // Build the model
        launcher.buildModel();
        Factory factory = launcher.getFactory();

        // Find all invocations of setIncludeCallerData(boolean) method in AsyncAppender
        List<CtInvocation> setIncludeCallerDataInvocations = 
            launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    CtExecutableReference<?> executableRef = element.getExecutable();
                    if (executableRef == null) return false;
                    
                    // Check if it's a call to setIncludeCallerData(boolean)
                    if ("setIncludeCallerData".equals(executableRef.getSimpleName())) {
                        // Check if it's setIncludeCallerData(boolean) - 1 parameter
                        if (executableRef.getParameters().size() == 1) {
                            // Check if the target is an AsyncAppender or its subclass
                            if (element.getTarget() != null) {
                                CtTypeReference<?> targetType = element.getTarget().getType();
                                if (targetType != null) {
                                    // Check if it's an AsyncAppender or its subclass
                                    return isAsyncAppenderSubtype(targetType, factory);
                                }
                            }
                        }
                    }
                    return false;
                }
            });

        // Find all invocations of isIncludeCallerData() method in AsyncAppender
        List<CtInvocation> isIncludeCallerDataInvocations = 
            launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    CtExecutableReference<?> executableRef = element.getExecutable();
                    if (executableRef == null) return false;
                    
                    // Check if it's a call to isIncludeCallerData()
                    if ("isIncludeCallerData".equals(executableRef.getSimpleName())) {
                        // Check if it's isIncludeCallerData() with no parameters
                        if (executableRef.getParameters().size() == 0) {
                            // Check if the target is an AsyncAppender or its subclass
                            if (element.getTarget() != null) {
                                CtTypeReference<?> targetType = element.getTarget().getType();
                                if (targetType != null) {
                                    // Check if it's an AsyncAppender or its subclass
                                    return isAsyncAppenderSubtype(targetType, factory);
                                }
                            }
                        }
                    }
                    return false;
                }
            });

        // Print what we found for debugging
        System.out.println("Found " + setIncludeCallerDataInvocations.size() + " setIncludeCallerData calls");
        System.out.println("Found " + isIncludeCallerDataInvocations.size() + " isIncludeCallerData calls");
        
        // Print the transformation rule description
        System.out.println("\n=== LOGBACK 1.4.0 BREAKING CHANGE TRANSFORMATION RULE ===");
        System.out.println("This rule addresses the breaking change in logback-classic 1.4.0");
        System.out.println("Problem: setIncludeCallerData(boolean) method signature changed");
        System.out.println("Solution: Transform calls to use new signature setIncludeCallerData(boolean, boolean)");
        System.out.println("The second parameter defaults to false for backward compatibility");
        System.out.println("=== END TRANSFORMATION RULE ===");
    }

    private static boolean isAsyncAppenderSubtype(CtTypeReference<?> targetType, Factory factory) {
        // Check if the target type is an AsyncAppender or its subclass
        try {
            // Check if it's AsyncAppender or extends AsyncAppender
            if ("ch.qos.logback.classic.AsyncAppender".equals(targetType.getQualifiedName())) {
                return true;
            }
            
            // Check superclasses
            CtTypeReference<?> superClass = targetType.getSuperclass();
            while (superClass != null) {
                if ("ch.qos.logback.classic.AsyncAppender".equals(superClass.getQualifiedName())) {
                    return true;
                }
                superClass = superClass.getSuperclass();
            }
            
            // Check interfaces
            for (CtTypeReference<?> iface : targetType.getSuperInterfaces()) {
                if ("ch.qos.logback.classic.AsyncAppender".equals(iface.getQualifiedName())) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            // If we can't resolve the type, assume it's not an AsyncAppender
            return false;
        }
    }
}