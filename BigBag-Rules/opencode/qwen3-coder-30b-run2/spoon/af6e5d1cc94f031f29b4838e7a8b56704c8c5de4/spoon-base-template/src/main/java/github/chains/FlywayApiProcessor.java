package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;

/**
 * Generic Spoon transformation processor to fix Flyway breaking changes.
 * This handles common API changes that break projects when upgrading from older
 * Flyway versions to newer versions (like 9.17.0).
 */
public class FlywayApiProcessor extends AbstractProcessor<CtInvocation> {

    @Override
    public boolean isToBeProcessed(CtInvocation candidate) {
        // Match invocations on Flyway class methods that have changed
        if (candidate.getExecutable() == null) {
            return false;
        }
        
        CtExecutableReference<?> execRef = candidate.getExecutable();
        // Check if it's a method call on Flyway class
        if (execRef.getDeclaringType() != null) {
            String declaringTypeName = execRef.getDeclaringType().getQualifiedName();
            return declaringTypeName.startsWith("org.flywaydb.core.Flyway");
        }
        return false;
    }

    @Override
    public void process(CtInvocation invocation) {
        // Simple approach: replace flyway instantiation with FluentConfiguration approach
        // This catches cases where new Flyway() is called without parameters
        
        if (invocation.getExecutable() != null && 
            invocation.getExecutable().getSimpleName().equals("<init>") &&
            invocation.getExecutable().getDeclaringType() != null &&
            invocation.getExecutable().getDeclaringType().getQualifiedName().startsWith("org.flywaydb.core.Flyway")) {
            
            // If it's the default constructor (no parameters)
            if (invocation.getArguments().size() == 0) {
                // Replace with: Flyway.configure().load()
                CtTypeReference<?> fluentConfigType = getFactory().Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
                
                // Create type access: org.flywaydb.core.api.configuration.FluentConfiguration
                CtTypeAccess<?> typeAccess = getFactory().Code().createTypeAccess(fluentConfigType);
                
                // Create the configure method call: FluentConfiguration.configure()
                CtExecutableReference<?> configureMethodRef = getFactory().Executable().createReference(
                    fluentConfigType.getTypeDeclaration().getMethod("configure"));
                CtExpression<?> configureCall = getFactory().Code().createInvocation(typeAccess, configureMethodRef);
                
                // Create the load method call: configure().load()
                CtExecutableReference<?> loadMethodRef = getFactory().Executable().createReference(
                    fluentConfigType.getTypeDeclaration().getMethod("load"));
                CtExpression<?> loadCall = getFactory().Code().createInvocation(configureCall, loadMethodRef);
                
                // Replace the entire invocation
                invocation.replace(loadCall);
            }
        }
    }
}