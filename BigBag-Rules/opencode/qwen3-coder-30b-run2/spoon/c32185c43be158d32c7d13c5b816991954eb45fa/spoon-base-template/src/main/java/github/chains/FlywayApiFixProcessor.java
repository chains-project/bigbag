package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;

/**
 * Generic transformation to fix Flyway API breaking changes introduced in version 9.10.2.
 * 
 * This transformation addresses:
 * 1. Constructor change: new Flyway() → Flyway.configure().load()
 */
public class FlywayApiFixProcessor extends AbstractProcessor<CtConstructorCall> {
    
    @Override
    public void process(CtConstructorCall constructorCall) {
        // Fix the Flyway constructor call
        if (constructorCall.getType().getSimpleName().equals("Flyway") && 
            constructorCall.getArguments().isEmpty()) {
            
            // Transform from new Flyway() to Flyway.configure().load()
            Factory factory = constructorCall.getFactory();
            
            // Create the new invocation: Flyway.configure().load()
            CtInvocation<?> configureInvocation = factory.createInvocation(
                factory.createTypeAccess(factory.Type().createReference("org.flywaydb.core.Flyway")),
                factory.createExecutableReference().setSimpleName("configure")
            );
            
            CtInvocation<?> loadInvocation = factory.createInvocation(configureInvocation, 
                factory.createExecutableReference().setSimpleName("load"));
                
            constructorCall.replace(loadInvocation);
        }
    }
}