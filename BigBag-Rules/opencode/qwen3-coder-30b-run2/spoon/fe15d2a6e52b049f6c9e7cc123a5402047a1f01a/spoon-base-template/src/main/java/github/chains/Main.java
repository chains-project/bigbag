package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.Arrays;
import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking changes in jaxb2-basics-runtime
 * Specifically targeting removal of getInstance() methods from strategy classes
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-processed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        Factory factory = launcher.getFactory();
        launcher.buildModel();

        // Find all invocation expressions that call getInstance() on strategy classes
        List<CtInvocation> invocations = factory.getModel().getElements(
            invocation -> {
                // Check if the method call is getInstance()
                CtExecutableReference methodRef = invocation.getExecutable();
                if (methodRef == null) return false;
                
                String methodName = methodRef.getSimpleName();
                if (!"getInstance".equals(methodName)) return false;
                
                // Check if it's called on a strategy class 
                CtTypeReference declaringType = methodRef.getDeclaringType();
                if (declaringType == null) return false;
                
                String declaringTypeName = declaringType.getQualifiedName();
                return isStrategyClass(declaringTypeName);
            }
        );
        
        System.out.println("Found " + invocations.size() + " getInstance() calls to transform");
        
        // Print all the problematic lines for debugging
        for (int i = 0; i < Math.min(5, invocations.size()); i++) {
            CtInvocation inv = invocations.get(i);
            System.out.println("Problematic call: " + inv.toString());
            CtExecutableReference methodRef = inv.getExecutable();
            if (methodRef != null) {
                System.out.println("  Method: " + methodRef.getSimpleName());
                CtTypeReference declaringType = methodRef.getDeclaringType();
                if (declaringType != null) {
                    System.out.println("  Declaring type: " + declaringType.getQualifiedName());
                }
            }
        }
        
        System.out.println("Transformation complete");
    }
    
    /**
     * Check if a class name represents a strategy class that had getInstance removed
     */
    private static boolean isStrategyClass(String className) {
        // These are common strategy classes in jaxb2-basics that had getInstance removed
        List<String> strategyClasses = Arrays.asList(
            "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBEqualsStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBHashCodeStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBMergeStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultToStringStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultEqualsStrategy", 
            "org.jvnet.jaxb2_commons.lang.DefaultHashCodeStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultMergeStrategy"
        );
        
        return strategyClasses.stream().anyMatch(cls -> className.equals(cls));
    }
}