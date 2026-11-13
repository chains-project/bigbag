package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation to fix breaking changes in jcabi-aspects dependency.
 * Specifically replaces Tv.TWENTY with 20 and Tv.FIVE with 5.
 */
public class Main {
    public static void main(String[] args) {
        // Default source directory - can be overridden by command line argument
        String sourceDirectory = "/workspace/jcabi-github/src";
        
        // Allow command line argument for source directory
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        System.out.println("Processing Java files in: " + sourceDirectory);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory);
        
        // Build the model
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Find all field accesses to Tv.TWENTY and Tv.FIVE and replace them
        factory.createQuery(new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class) {
            @Override
            public boolean matches(CtFieldAccess<?> element) {
                if (element.getVariable() instanceof CtFieldReference) {
                    CtFieldReference<?> fieldRef = (CtFieldReference<?>) element.getVariable();
                    return fieldRef.getDeclaringType() != null && 
                           fieldRef.getDeclaringType().getQualifiedName().equals("com.jcabi.aspects.Tv") &&
                           (fieldRef.getSimpleName().equals("TWENTY") || fieldRef.getSimpleName().equals("FIVE"));
                }
                return false;
            }
        }).forEach(fieldAccess -> {
            if (fieldAccess.getVariable() instanceof CtFieldReference) {
                CtFieldReference<?> fieldRef = (CtFieldReference<?>) fieldAccess.getVariable();
                if (fieldRef.getSimpleName().equals("TWENTY")) {
                    // Replace with literal 20
                    fieldAccess.replace(factory.Code().createLiteral(20));
                } else if (fieldRef.getSimpleName().equals("FIVE")) {
                    // Replace with literal 5
                    fieldAccess.replace(factory.Code().createLiteral(5));
                }
            }
        });
        
        // Process and write the modified files back
        launcher.process();
        
        System.out.println("Transformation completed successfully!");
        System.out.println("Files in " + sourceDirectory + " have been updated.");
    }
}