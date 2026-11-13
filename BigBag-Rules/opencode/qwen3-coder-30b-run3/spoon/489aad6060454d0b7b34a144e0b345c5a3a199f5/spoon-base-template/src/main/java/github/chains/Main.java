package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Flyway constructor calls that broke due to API changes.
 * This transformation replaces new Flyway() calls with new Flyway(Configuration) calls.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Find all Flyway constructor calls
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                return element.getType() != null && 
                       "org.flywaydb.core.Flyway".equals(element.getType().getQualifiedName()) &&
                       element.getArguments().isEmpty(); // Empty args indicate the old constructor
            }
        });
        
        System.out.println("Found " + constructorCalls.size() + " Flyway constructor calls to transform.");
        
        // Transform each constructor call
        for (CtConstructorCall<?> call : constructorCalls) {
            // Create a FluentConfiguration.configure() invocation
            CtExecutableReference<?> fluentConfigureRef = call.getFactory().createExecutableReference();
            fluentConfigureRef.setStatic(true);
            fluentConfigureRef.setDeclaringType(call.getFactory().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
            fluentConfigureRef.setSimpleName("configure");
            
            // Create the method call: FluentConfiguration.configure()
            CtInvocation<?> fluentCall = call.getFactory().createInvocation(
                null, 
                fluentConfigureRef,
                call.getFactory().createLiteral("") // Empty string for default class loader
            );
            
            // Add the FluentConfiguration.configure() call as argument to the new Flyway constructor
            call.addArgument(fluentCall);
        }
        
        launcher.process();
        System.out.println("Transformation completed. Fixed files saved to " + sourceDirectory + "_fixed");
    }
}