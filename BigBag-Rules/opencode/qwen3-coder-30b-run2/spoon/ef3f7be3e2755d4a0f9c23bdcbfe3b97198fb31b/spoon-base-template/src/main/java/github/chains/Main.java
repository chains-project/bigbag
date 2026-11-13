package github.chains;

import spoon.Launcher;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.Query;

import java.util.List;

/**
 * Generic transformation to fix breaking dependency changes in tinspin-indexes
 * Specifically addresses the renaming of query1NN to query1nn method
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        
        // Create Spoon factory
        Launcher launcher = new Launcher();
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.addInputResource(sourceDir);
        launcher.buildModel();
        
        // Apply transformations
        applyTransformations(launcher.getFactory());
        
        // Print the transformed model
        launcher.prettyprint();
    }
    
    /**
     * Apply all transformations to fix the breaking changes in tinspin-indexes
     */
    private static void applyTransformations(Factory factory) {
        // Transform query1NN method calls to query1nn
        transformQuery1NNCalls(factory);
    }
    
    /**
     * Transform query1NN method calls to query1nn
     */
    private static void transformQuery1NNCalls(Factory factory) {
        // Find method invocations with name query1NN
        List<CtInvocation<?>> invocations = Query.getElements(factory, new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                try {
                    CtExecutableReference<?> methodRef = element.getExecutable();
                    if (methodRef != null) {
                        return "query1NN".equals(methodRef.getSimpleName());
                    }
                } catch (Exception e) {
                    // Silently ignore if we can't resolve the method
                }
                return false;
            }
        });
        
        // Replace method name with query1nn
        for (CtInvocation<?> invocation : invocations) {
            try {
                CtExecutableReference<?> methodRef = invocation.getExecutable();
                if (methodRef != null) {
                    methodRef.setSimpleName("query1nn");
                }
            } catch (Exception e) {
                System.err.println("Failed to transform method call: " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + invocations.size() + " calls to query1nn");
    }
}