package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.*;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <sourceDirectory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all constructor calls to Flyway()
        List<CtConstructorCall<?>> flywayConstructorCalls = Query.getElements(factory, new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                CtTypeReference typeRef = element.getType();
                return typeRef != null && "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName());
            }
        });
        
        System.out.println("Found " + flywayConstructorCalls.size() + " Flyway constructor calls");
        
        for (CtConstructorCall<?> constructorCall : flywayConstructorCalls) {
            try {
                transformFlywayConstructor(constructorCall, factory);
            } catch (Exception e) {
                System.err.println("Error transforming constructor at " + constructorCall.getPosition() + ": " + e.getMessage());
            }
        }
        
        // Write transformed code back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void transformFlywayConstructor(CtConstructorCall<?> constructorCall, Factory factory) {
        // Create basic replacement: Flyway.configure().load()
        CtTypeReference flywayType = factory.createReference("org.flywaydb.core.Flyway");
        CtExecutableReference configureMethod = factory.createExecutableReference();
        configureMethod.setType(flywayType);
        configureMethod.setSimpleName("configure");
        
        CtInvocation<?> configureCall = factory.createInvocation();
        configureCall.setExecutable(configureMethod);
        
        // Add .load() to get Flyway instance
        CtTypeReference fluentConfigType = factory.createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
        CtExecutableReference loadMethod = factory.createExecutableReference();
        loadMethod.setType(fluentConfigType);
        loadMethod.setSimpleName("load");
        
        CtInvocation<?> loadCall = factory.createInvocation();
        loadCall.setTarget(configureCall);
        loadCall.setExecutable(loadMethod);
        
        // Replace the constructor call with the new invocation
        constructorCall.replace(loadCall);
        
        System.out.println("Transformed Flyway constructor at: " + constructorCall.getPosition());
        
        // Note: In a complete implementation, we would also:
        // 1. Find the variable name if this is a variable declaration
        // 2. Collect subsequent method calls on that variable
        // 3. Transform setter calls to fluent API calls
        // 4. Handle setClassLoader specially by passing to configure() method
        // This simplified version just replaces the constructor
    }
}