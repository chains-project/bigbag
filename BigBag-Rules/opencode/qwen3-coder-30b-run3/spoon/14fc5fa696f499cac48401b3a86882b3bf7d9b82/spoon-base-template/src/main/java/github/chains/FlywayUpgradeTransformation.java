package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class FlywayUpgradeTransformation extends AbstractProcessor<CtNewClass> {

    @Override
    public void process(CtNewClass flywayConstructor) {
        // Only process Flyway constructors with no arguments (the problematic pattern)
        if (flywayConstructor.getType() == null ||
            !"org.flywaydb.core.Flyway".equals(flywayConstructor.getType().getQualifiedName())) {
            return;
        }
        
        // Make sure it's a no-argument constructor
        if (!flywayConstructor.getArguments().isEmpty()) {
            return;
        }
        
        // Find the enclosing method to get context
        CtMethod<?> enclosingMethod = flywayConstructor.getParent(CtMethod.class);
        if (enclosingMethod == null) {
            return;
        }
        
        // Find the variable this Flyway instance is assigned to
        CtVariableWrite<?> flywayVariable = findFlywayVariableAssignment(flywayConstructor);
        if (flywayVariable == null) {
            return;
        }
        
        // Now find all setter calls on this variable in the same method
        List<CtInvocation<?>> setterCalls = findFlywaySetterCalls(enclosingMethod, flywayVariable.getVariable().getSimpleName());
        
        if (!setterCalls.isEmpty()) {
            // Transform to new API with FluentConfiguration
            transformToNewFlywayAPI(flywayConstructor, setterCalls);
        }
    }
    
    private CtVariableWrite<?> findFlywayVariableAssignment(CtNewClass flywayConstructor) {
        // Walk up the AST to find the assignment
        CtElement parent = flywayConstructor.getParent();
        while (parent != null) {
            if (parent instanceof CtVariableWrite<?>) {
                return (CtVariableWrite<?>) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
    
    private List<CtInvocation<?>> findFlywaySetterCalls(CtMethod<?> method, String variableName) {
        return method.getElements(new TypeFilter<>(CtInvocation.class))
            .stream()
            .filter(inv -> inv.getTarget() instanceof CtVariableRead &&
                          ((CtVariableRead) inv.getTarget()).getVariable().getSimpleName().equals(variableName) &&
                          inv.getExecutable() != null && 
                          inv.getExecutable().getSimpleName().startsWith("set"))
            .toList();
    }
    
    private void transformToNewFlywayAPI(CtNewClass oldFlywayConstructor, List<CtInvocation<?>> setterCalls) {
        Factory factory = getFactory();
        
        // Create new pattern using FluentConfiguration:
        // org.flywaydb.core.api.configuration.FluentConfiguration.configure()
        //   .dataSource(dataSource)
        //   .classLoader(classLoader)
        //   .locations(locations)
        //   .validateOnMigrate(validateValue)
        //   .load()
        
        // Start with FluentConfiguration.configure()
        CtInvocation<?> fluentConfigCall = factory.createInvocation(
            factory.createTypeAccess(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration")),
            factory.createExecutableReference()
                .setSimpleName("configure")
                .setType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"))
        );
        
        // Add configuration calls based on the original setters
        for (CtInvocation<?> setterCall : setterCalls) {
            String setterName = setterCall.getExecutable().getSimpleName();
            List<CtExpression<?>> arguments = setterCall.getArguments();
            
            // Map old setter names to new method names
            String newMethodName = mapSetterToNewMethod(setterName);
            
            if (newMethodName != null && !arguments.isEmpty()) {
                CtExecutableReference<?> executableRef = factory.createExecutableReference()
                    .setSimpleName(newMethodName)
                    .setType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
                    
                CtInvocation<?> newSetterCall = factory.createInvocation(
                    fluentConfigCall,
                    executableRef
                );
                
                // Add all arguments from the old setter call
                for (CtExpression<?> arg : arguments) {
                    newSetterCall.addArgument(arg);
                }
                
                fluentConfigCall = newSetterCall;
            }
        }
        
        // End with .load() to create the Flyway instance
        CtInvocation<?> loadCall = factory.createInvocation(
            fluentConfigCall,
            factory.createExecutableReference()
                .setSimpleName("load")
                .setType(factory.Type().createReference("org.flywaydb.core.Flyway"))
        );
        
        // Replace the old construction with new pattern
        oldFlywayConstructor.replace(loadCall);
    }
    
    private String mapSetterToNewMethod(String setterName) {
        switch (setterName) {
            case "setDataSource":
                return "dataSource";
            case "setClassLoader":
                return "classLoader";
            case "setLocations":
                return "locations";
            case "setValidateOnMigrate":
                return "validateOnMigrate";
            default:
                return null;
        }
    }
}