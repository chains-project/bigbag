package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Flyway API breaking changes.
 * This transformation updates calls to the old Flyway constructor (new Flyway()) 
 * to use the new constructor that requires a Configuration parameter.
 * 
 * The transformation specifically targets:
 * - Flyway constructor calls with no arguments
 * - Replaces them with calls using the FluentConfiguration approach
 */
public class FlywayFixTransformer {
    
    public static void main(String[] args) {
        // Initialize the Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to transform
        String sourceDirectory = "/workspace/nem";
        launcher.addInputResource(sourceDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply the transformation
        fixFlywayConstructorCalls(model);
        
        // Print some stats
        System.out.println("Transformation complete.");
    }
    
    /**
     * Fixes Flyway constructor calls that use the deprecated no-argument constructor.
     * Replaces calls to new Flyway() with new Flyway(configuration) where configuration
     * is obtained via FluentConfiguration.configure().
     */
    private static void fixFlywayConstructorCalls(CtModel model) {
        // Find all new expressions that create Flyway objects with no arguments
        List<CtNewClass> flywayNewExpressions = model.getElements(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                if (element.getType() == null) return false;
                return element.getType().getQualifiedName().equals("org.flywaydb.core.Flyway") &&
                       element.getArguments().isEmpty();
            }
        });
        
        // Process each Flyway constructor call
        for (CtNewClass flywayNewExpression : flywayNewExpressions) {
            // Find the assignment this expression is part of (if it's assigned to a variable)
            CtElement parent = flywayNewExpression.getParent();
            // Note: Parent can be CtVariableAssignment or other expressions
            if (parent != null && parent instanceof CtVariableAssignment) {
                CtVariableAssignment<?> assignment = (CtVariableAssignment<?>) parent;
                
                // Create the new Flyway construction with FluentConfiguration.configure()
                CtNewClass newFlywayCall = createConfigurationBasedFlywayCall(flywayNewExpression);
                
                // Replace the assignment with the new construction
                assignment.replace(newFlywayCall);
            }
        }
    }
    
    /**
     * Creates a new Flyway constructor call with configuration parameter using FluentConfiguration
     */
    private static CtNewClass createConfigurationBasedFlywayCall(CtNewClass original) {
        CtNewClass newFlywayCall = original.clone();
        
        // Create the configuration setup using FluentConfiguration.configure()
        CtInvocation fluentConfigure = createFluentConfigureCall(original);
        
        // Set the configuration as the argument to the Flyway constructor
        newFlywayCall.setArguments(List.of(fluentConfigure));
        
        return newFlywayCall;
    }
    
    /**
     * Creates a FluentConfiguration.configure() call
     */
    private static CtInvocation createFluentConfigureCall(CtElement context) {
        CtInvocation fluentConfigure = context.getFactory().createInvocation();
        CtTypeReference<?> flywayType = context.getFactory().createTypeReference(org.flywaydb.core.Flyway.class);
        fluentConfigure.setTarget(context.getFactory().createTypeAccess(flywayType));
        fluentConfigure.setExecutable(
            context.getFactory().createExecutableReference()
                .setStatic(true)
                .setDeclaringType(flywayType)
                .setSimpleName("configure")
        );
        return fluentConfigure;
    }
}