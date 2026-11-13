package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic Spoon transformation rule to fix jaxb2-basics-runtime API breakage
 * Replaces calls to deprecated getInstance() methods on strategy classes with
 * static INSTANCE fields from the new API.
 */
public class Main {
    public static void main(String[] args) {
        // Set the source directory to transform
        String sourceDir = "/workspace/billy";
        String outputDir = "/workspace/billy-transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        launcher.buildModel();
        
        // Get the Spoon model
        Factory factory = launcher.getFactory();
        
        // Transform all getInstance() calls on strategy classes
        transformStrategyCalls(factory);
        
        // Generate the output
        launcher.process();
        
        // Print a summary
        System.out.println("Transformation complete. Fixed files written to: " + outputDir);
    }
    
    /**
     * Transforms calls to deprecated getInstance() methods on jaxb2-basics strategy classes
     * to use the new static INSTANCE fields.
     */
    private static void transformStrategyCalls(Factory factory) {
        // Find all method invocations
        List<CtInvocation<?>> invocations = factory.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        
        // Map of old method signatures to new static fields
        Map<String, String> replacements = new HashMap<>();
        
        // Strategy classes that had getInstance() removed
        replacements.put("org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy.getInstance()", 
                         "org.jvnet.jaxb2_commons.lang.DefaultToStringStrategy.INSTANCE");
        replacements.put("org.jvnet.jaxb2_commons.lang.JAXBEqualsStrategy.getInstance()", 
                         "org.jvnet.jaxb2_commons.lang.DefaultEqualsStrategy.INSTANCE");
        replacements.put("org.jvnet.jaxb2_commons.lang.JAXBHashCodeStrategy.getInstance()", 
                         "org.jvnet.jaxb2_commons.lang.DefaultHashCodeStrategy.INSTANCE");
        replacements.put("org.jvnet.jaxb2_commons.lang.JAXBMergeStrategy.getInstance()", 
                         "org.jvnet.jaxb2_commons.lang.DefaultMergeStrategy.INSTANCE");
        replacements.put("org.jvnet.jaxb2_commons.lang.JAXBCopyStrategy.getInstance()", 
                         "org.jvnet.jaxb2_commons.lang.DefaultCopyStrategy.INSTANCE");

        for (CtInvocation<?> invocation : invocations) {
            // Check if this is a getInstance() call
            CtExecutableReference<?> methodRef = invocation.getExecutable();
            
            if (methodRef != null && methodRef.getSimpleName().equals("getInstance")) {
                // Get the declaring type
                if (methodRef.getDeclaringType() != null) {
                    String fullTypeName = methodRef.getDeclaringType().getQualifiedName();
                    
                    // Check if this is one of the deprecated classes
                    if (replacements.containsKey(fullTypeName + ".getInstance()")) {
                        String newField = replacements.get(fullTypeName + ".getInstance()");
                        // Replace with new static field reference
                        invocation.replace(newField);
                    }
                }
            }
        }
    }
}