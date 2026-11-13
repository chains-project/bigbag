package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule for Dropwizard 4.0.0 breaking API change:
 * JerseyClientBuilder constructor parameter type changed from 
 * io.dropwizard.setup.Environment to io.dropwizard.core.setup.Environment
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String OLD_ENVIRONMENT_TYPE = "io.dropwizard.setup.Environment";
    private static final String NEW_ENVIRONMENT_TYPE = "io.dropwizard.core.setup.Environment";
    private static final String JERSEY_CLIENT_BUILDER_TYPE = "io.dropwizard.client.JerseyClientBuilder";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying transformation to: " + sourceDirectory);
        
        try {
            applyTransformation(sourceDirectory);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void applyTransformation(String sourceDirectory) {
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setNoClasspath(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to JerseyClientBuilder
        List<CtConstructorCall<?>> constructorCalls = model
            .getElements(new TypeFilter<>(CtConstructorCall.class));
        
        int transformationsApplied = 0;
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            if (isJerseyClientBuilderConstructorCall(constructorCall)) {
                if (transformConstructorCall(constructorCall)) {
                    transformationsApplied++;
                }
            }
        }
        
        System.out.println("Applied " + transformationsApplied + " transformations");
        
        // Write the transformed code back to files
        launcher.prettyprint();
    }
    
    private static boolean isJerseyClientBuilderConstructorCall(CtConstructorCall<?> constructorCall) {
        CtTypeReference<?> constructedType = constructorCall.getType();
        return constructedType != null && 
               JERSEY_CLIENT_BUILDER_TYPE.equals(constructedType.getQualifiedName());
    }
    
    private static boolean transformConstructorCall(CtConstructorCall<?> constructorCall) {
        // Check if this constructor call has exactly one argument
        if (constructorCall.getArguments().size() != 1) {
            return false;
        }
        
        // Get the argument type
        CtTypeReference<?> argumentType = constructorCall.getArguments().get(0).getType();
        if (argumentType == null) {
            return false;
        }
        
        // Check if the argument type is the old Environment type
        String argumentTypeName = argumentType.getQualifiedName();
        if (OLD_ENVIRONMENT_TYPE.equals(argumentTypeName)) {
            // Replace the type reference in the constructor call
            CtTypeReference<?> newTypeRef = constructorCall.getFactory()
                .createReference(NEW_ENVIRONMENT_TYPE);
            
            // Update the argument's type
            constructorCall.getArguments().get(0).setType(newTypeRef);
            
            // Also update any imports in the containing class
            CtType<?> containingType = constructorCall.getParent(CtType.class);
            if (containingType != null) {
                updateImports(containingType);
            }
            
            System.out.println("Transformed JerseyClientBuilder constructor call at: " + 
                constructorCall.getPosition());
            return true;
        }
        
        return false;
    }
    
    private static void updateImports(CtType<?> containingType) {
        // Import management is handled automatically by Spoon's auto-import feature
        // The type references in the code will be automatically updated
        // and imports will be adjusted accordingly
    }
}