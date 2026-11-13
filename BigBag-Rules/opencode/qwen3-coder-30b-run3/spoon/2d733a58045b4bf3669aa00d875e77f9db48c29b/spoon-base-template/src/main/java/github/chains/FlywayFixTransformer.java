package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

/**
 * A generic Spoon transformation that fixes Flyway API usage patterns
 * to address breaking changes between Flyway versions.
 */
public class FlywayFixTransformer extends AbstractProcessor<CtInvocation> {

    private static final String FLYWAY_CLASS = "org.flywaydb.core.Flyway";
    private static final String FLUENT_CONFIGURATION_CLASS = "org.flywaydb.core.api.configuration.FluentConfiguration";
    
    @Override
    public boolean isToBeProcessed(CtInvocation candidate) {
        // Process all invocation statements
        return true;
    }

    @Override
    public void process(CtInvocation invocation) {
        // Check if this is a Flyway API call that needs fixing
        if (isFlywayApiCall(invocation)) {
            // Apply specific transformation logic for Flyway API changes
            System.out.println("Detected Flyway API usage: " + invocation.toString());
            
            // Fix deprecated setValidateOnMigrate usage pattern
            fixSetValidateOnMigrate(invocation);
        }
    }

    /**
     * Determines if the invocation is related to Flyway API
     */
    private boolean isFlywayApiCall(CtInvocation invocation) {
        // Check the target of the invocation to see if it's a Flyway object
        CtExpression target = invocation.getTarget();
        if (target != null) {
            // Check if target is a Flyway instance
            if (target.getType() != null) {
                String targetType = target.getType().toString();
                if (targetType.contains("Flyway")) {
                    return true;
                }
            }
        }
        
        // Check if the method belongs to Flyway class
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable != null && executable.getDeclaringType() != null) {
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType.toString().contains("Flyway")) {
                return true;
            }
        }
        
        // Check method name patterns commonly used in Flyway
        String methodName = invocation.getExecutable().getSimpleName();
        return methodName.contains("validate") || 
               methodName.contains("migrate") ||
               methodName.contains("set") ||
               methodName.contains("configure") ||
               methodName.contains("Flyway");
    }

    /**
     * Fixes deprecated setValidateOnMigrate() usage by replacing it with validateOnMigrate()
     */
    private void fixSetValidateOnMigrate(CtInvocation invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        
        // Look for setValidateOnMigrate method calls
        if ("setValidateOnMigrate".equals(methodName)) {
            // This is a deprecated method call
            // We need to replace it with validateOnMigrate() in the chain
            
            // Get the target (Flyway instance or FluentConfiguration)
            CtExpression target = invocation.getTarget();
            
            if (target != null) {
                // The replacement pattern should be:
                // .setValidateOnMigrate(true) becomes .validateOnMigrate(true)
                
                // Create a new invocation with the fixed method name
                try {
                    // Clone invocation to create the new one with the correct method name
                    CtInvocation newInvocation = invocation.clone();
                    newInvocation.getExecutable().setSimpleName("validateOnMigrate");
                    
                    // Replace the old invocation with the new one
                    invocation.replace(newInvocation);
                    System.out.println("Fixed Flyway API usage: " + invocation.toString() + " -> " + newInvocation.toString());
                } catch (Exception e) {
                    System.err.println("Failed to fix Flyway API usage: " + e.getMessage());
                }
            }
        }
    }
}