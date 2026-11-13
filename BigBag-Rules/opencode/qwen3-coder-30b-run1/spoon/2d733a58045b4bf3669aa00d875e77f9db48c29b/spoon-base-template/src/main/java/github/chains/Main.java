package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Add input directories to analyze
        launcher.addInputResource("/workspace/nem");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Apply transformation to fix Flyway API changes
        fixFlywayApiChanges(model);
        
        // Write the modified code back to the source
        launcher.setSourceOutputDirectory("/workspace/nem");
        launcher.process();
        
        System.out.println("Flyway API fix applied successfully!");
    }
    
    private static void fixFlywayApiChanges(CtModel model) {
        // 1. Find all Flyway constructor calls with no arguments and replace with new API
        model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                return element.getType().getSimpleName().equals("Flyway") && 
                       element.getArguments().isEmpty();
            }
        }).forEach(constructorCall -> {
            // Replace with: new Flyway(FluentConfiguration.configure())
            Factory factory = constructorCall.getFactory();
            
            // Create FluentConfiguration.configure() invocation
            CtTypeAccess fluentConfigType = factory.createTypeAccess(
                factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration")
            );
            
            CtInvocation<?> configureInvocation = factory.createInvocation();
            configureInvocation.setTarget(fluentConfigType);
            configureInvocation.setExecutable(
                factory.createExecutableReference()
                    .setSimpleName("configure")
                    .setDeclaringType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"))
            );
            
            // Create new constructor call with Configuration parameter
            CtConstructorCall<?> newConstructorCall = factory.createConstructorCall(
                constructorCall.getType()
            );
            newConstructorCall.getArguments().add(configureInvocation);
            
            constructorCall.replace(newConstructorCall);
        });
        
        // 2. Find all setDataSource calls on Flyway instances and convert them
        model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setDataSource") &&
                       element.getTarget() != null &&
                       element.getTarget().getType().getSimpleName().equals("Flyway");
            }
        }).forEach(invocation -> {
            // Convert to: flyway.dataSource(...)
            Factory factory = invocation.getFactory();
            
            // Create new executable reference for dataSource method
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            
            CtInvocation<?> newInvocation = factory.createInvocation();
            newInvocation.setTarget(invocation.getTarget());
            newInvocation.setExecutable(
                factory.createExecutableReference()
                    .setSimpleName("dataSource")
                    .setDeclaringType(fluentConfigType)
            );
            newInvocation.getArguments().addAll(invocation.getArguments());
            
            invocation.replace(newInvocation);
        });
        
        // 3. Find all setClassLoader calls on Flyway instances and convert them
        model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setClassLoader") &&
                       element.getTarget() != null &&
                       element.getTarget().getType().getSimpleName().equals("Flyway");
            }
        }).forEach(invocation -> {
            // Convert to: flyway.classLoader(...)
            Factory factory = invocation.getFactory();
            
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            
            CtInvocation<?> newInvocation = factory.createInvocation();
            newInvocation.setTarget(invocation.getTarget());
            newInvocation.setExecutable(
                factory.createExecutableReference()
                    .setSimpleName("classLoader")
                    .setDeclaringType(fluentConfigType)
            );
            newInvocation.getArguments().addAll(invocation.getArguments());
            
            invocation.replace(newInvocation);
        });
        
        // 4. Find all setLocations calls on Flyway instances and convert them
        model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setLocations") &&
                       element.getTarget() != null &&
                       element.getTarget().getType().getSimpleName().equals("Flyway");
            }
        }).forEach(invocation -> {
            // Convert to: flyway.locations(...)
            Factory factory = invocation.getFactory();
            
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            
            CtInvocation<?> newInvocation = factory.createInvocation();
            newInvocation.setTarget(invocation.getTarget());
            newInvocation.setExecutable(
                factory.createExecutableReference()
                    .setSimpleName("locations")
                    .setDeclaringType(fluentConfigType)
            );
            newInvocation.getArguments().addAll(invocation.getArguments());
            
            invocation.replace(newInvocation);
        });
        
        // 5. Find all setValidateOnMigrate calls on Flyway instances and convert them
        model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setValidateOnMigrate") &&
                       element.getTarget() != null &&
                       element.getTarget().getType().getSimpleName().equals("Flyway");
            }
        }).forEach(invocation -> {
            // Convert to: flyway.validateOnMigrate(...)
            Factory factory = invocation.getFactory();
            
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            
            CtInvocation<?> newInvocation = factory.createInvocation();
            newInvocation.setTarget(invocation.getTarget());
            newInvocation.setExecutable(
                factory.createExecutableReference()
                    .setSimpleName("validateOnMigrate")
                    .setDeclaringType(fluentConfigType)
            );
            newInvocation.getArguments().addAll(invocation.getArguments());
            
            invocation.replace(newInvocation);
        });
    }
}