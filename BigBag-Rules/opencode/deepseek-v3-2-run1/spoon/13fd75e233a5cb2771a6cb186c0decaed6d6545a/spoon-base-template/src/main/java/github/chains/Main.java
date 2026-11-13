package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Hamcrest StringContains/StringStartsWith constructor fix to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Create the model
        launcher.buildModel();
        
        // Counter for tracking changes - needs to be effectively final for lambda
        final int[] fixedCount = {0};
        
        // Find all constructor calls to StringContains or StringStartsWith
        launcher.getModel().getRootPackage().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> constructorCall) {
                if (constructorCall.getType() == null) {
                    return false;
                }
                
                String typeName = constructorCall.getType().getQualifiedName();
                boolean isStringContains = "org.hamcrest.core.StringContains".equals(typeName);
                boolean isStringStartsWith = "org.hamcrest.core.StringStartsWith".equals(typeName);
                
                return isStringContains || isStringStartsWith;
            }
        }).forEach(constructorCall -> {
            String typeName = constructorCall.getType().getQualifiedName();
            int argCount = constructorCall.getArguments().size();
            
            // We need to handle two cases:
            // 1. Old pattern: new StringContains(String) - needs to add boolean parameter
            // 2. Current code has new pattern but Hamcrest lib is old - would need to remove boolean parameter
            //    But we can't easily determine which Hamcrest version is being used
            
            // For now, we'll transform old pattern to new pattern
            // This is the most common case when upgrading Hamcrest dependency
            if (argCount == 1) {
                System.out.println("Found old pattern constructor call to fix: " + typeName);
                System.out.println("  Position: " + constructorCall.getPosition());
                
                // Determine default boolean value based on matcher type and typical usage
                boolean ignoreCase = true; // Default to case-insensitive for StringContains
                if ("org.hamcrest.core.StringStartsWith".equals(typeName)) {
                    // StringStartsWith is typically for paths/URLs which are case-sensitive
                    ignoreCase = false;
                }
                
                // Create a new constructor call with boolean parameter added
                CtConstructorCall<?> newConstructorCall = constructorCall.getFactory().createConstructorCall(
                    constructorCall.getType(),
                    constructorCall.getFactory().createLiteral(ignoreCase),
                    constructorCall.getArguments().get(0)
                );
                
                // Replace the old constructor call with the new one
                constructorCall.replace(newConstructorCall);
                System.out.println("  Fixed: added boolean '" + ignoreCase + "' parameter for " + 
                    (ignoreCase ? "case-insensitive" : "case-sensitive") + " matching");
                
                // Update counter
                fixedCount[0]++;
            } else if (argCount == 2) {
                // Already has boolean parameter - check if first arg is boolean literal
                // We could potentially remove it if compiling against old Hamcrest,
                // but that would lose case-sensitivity information
                System.out.println("Found new pattern constructor call (already has boolean): " + typeName);
                System.out.println("  Position: " + constructorCall.getPosition());
                System.out.println("  Note: Code already uses new API pattern with boolean parameter");
            }
        });
        
        if (fixedCount[0] > 0) {
            // Apply the transformation
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Transformation completed successfully! Fixed " + fixedCount[0] + " constructor calls.");
        } else {
            System.out.println("No transformations needed - all constructor calls already use new API pattern.");
        }
    }
}