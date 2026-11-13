package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add processor for Flyway API migration
        launcher.addProcessor(new FlywayApiMigrationProcessor());
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
    }
    
    static class FlywayApiMigrationProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> constructorCall) {
            // Check if this is a Flyway constructor call
            CtTypeReference<?> typeRef = constructorCall.getType();
            if (typeRef == null) {
                return false;
            }
            
            String typeName = typeRef.getQualifiedName();
            return "org.flywaydb.core.Flyway".equals(typeName) 
                   && constructorCall.getArguments().isEmpty();
        }
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            System.out.println("Found Flyway constructor call at: " + constructorCall.getPosition());
            
            // Find setter calls in the same block
            List<CtInvocation<?>> setterCalls = findSetterCalls(constructorCall);
            
            if (setterCalls.isEmpty()) {
                System.out.println("No setter calls found for this Flyway instance, using basic migration");
                migrateBasicConstructor(constructorCall);
            } else {
                System.out.println("Found " + setterCalls.size() + " setter calls, migrating to fluent API");
                migrateWithSetters(constructorCall, setterCalls);
            }
        }
        
        private List<CtInvocation<?>> findSetterCalls(CtConstructorCall<?> constructorCall) {
            List<CtInvocation<?>> setterCalls = new ArrayList<>();
            
            // Look for setter calls in the same block
            CtBlock<?> parentBlock = constructorCall.getParent(CtBlock.class);
            if (parentBlock == null) {
                return setterCalls;
            }
            
            // Get all statements after the constructor call
            List<CtStatement> statements = parentBlock.getStatements();
            int constructorIndex = -1;
            
            // Find the constructor call in statements
            for (int i = 0; i < statements.size(); i++) {
                if (statements.get(i).equals(constructorCall) || 
                    statements.get(i).toString().contains("new Flyway()")) {
                    constructorIndex = i;
                    break;
                }
            }
            
            if (constructorIndex == -1) {
                return setterCalls;
            }
            
            // Get the variable name if the constructor is assigned to a variable
            String targetVarName = null;
            CtStatement constructorStmt = statements.get(constructorIndex);
            if (constructorStmt instanceof CtLocalVariable) {
                CtLocalVariable<?> localVar = (CtLocalVariable<?>) constructorStmt;
                targetVarName = localVar.getSimpleName();
            } else if (constructorStmt instanceof CtAssignment) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) constructorStmt;
                if (assignment.getAssigned() instanceof CtVariableAccess) {
                    CtVariableAccess<?> varAccess = (CtVariableAccess<?>) assignment.getAssigned();
                    targetVarName = varAccess.getVariable().getSimpleName();
                }
            }
            
            // Check subsequent statements for setter calls on the same variable
            for (int i = constructorIndex + 1; i < statements.size(); i++) {
                CtStatement stmt = statements.get(i);
                if (stmt instanceof CtInvocation) {
                    CtInvocation<?> invocation = (CtInvocation<?>) stmt;
                    String methodName = invocation.getExecutable().getSimpleName();
                    
                    // Check if this is a setter call
                    if (methodName.startsWith("set")) {
                        // Check if it's called on our target variable
                        if (targetVarName != null) {
                            CtExpression<?> target = invocation.getTarget();
                            if (target instanceof CtVariableAccess) {
                                CtVariableAccess<?> varAccess = (CtVariableAccess<?>) target;
                                if (targetVarName.equals(varAccess.getVariable().getSimpleName())) {
                                    setterCalls.add(invocation);
                                    continue;
                                }
                            }
                        }
                        // If we can't determine the target, add it anyway
                        setterCalls.add(invocation);
                    } else {
                        // Stop at first non-setter method (unless we're collecting all in block)
                        break;
                    }
                } else {
                    // Stop at first non-invocation statement
                    break;
                }
            }
            
            return setterCalls;
        }
        
        private void migrateBasicConstructor(CtConstructorCall<?> constructorCall) {
            // Replace: new Flyway()
            // With: Flyway.configure().load()
            
            try {
                // Get the factory
                spoon.reflect.factory.Factory factory = getFactory();
                
                // Create type references
                CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
                
                // Create Flyway.configure() call
                CtTypeAccess<?> typeAccess = factory.createTypeAccess(flywayType);
                CtExecutableReference<?> configureRef = factory.createExecutableReference();
                configureRef.setSimpleName("configure");
                configureRef.setType(flywayType);
                
                CtInvocation<?> configureCall = factory.createInvocation(typeAccess, configureRef);
                
                // Create .load() call
                CtExecutableReference<?> loadRef = factory.createExecutableReference();
                loadRef.setSimpleName("load");
                loadRef.setType(flywayType);
                
                CtInvocation<?> loadCall = factory.createInvocation(configureCall, loadRef);
                
                // Replace the constructor call with the new chain
                constructorCall.replace(loadCall);
                System.out.println("Migrated basic Flyway constructor");
                
            } catch (Exception e) {
                System.err.println("Error migrating constructor: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        private void migrateWithSetters(CtConstructorCall<?> constructorCall, List<CtInvocation<?>> setterCalls) {
            try {
                // Get the factory
                spoon.reflect.factory.Factory factory = getFactory();
                
                // Create type references
                CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
                CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
                
                // Create Flyway.configure() call
                CtTypeAccess<?> typeAccess = factory.createTypeAccess(flywayType);
                CtExecutableReference<?> configureRef = factory.createExecutableReference();
                configureRef.setSimpleName("configure");
                configureRef.setType(flywayType);
                
                CtInvocation<?> currentCall = factory.createInvocation(typeAccess, configureRef);
                
                // Map old setter names to new fluent method names
                List<String> fluentMethodNames = new ArrayList<>();
                List<List<CtExpression<?>>> argumentsList = new ArrayList<>();
                
                for (CtInvocation<?> setterCall : setterCalls) {
                    String setterName = setterCall.getExecutable().getSimpleName();
                    String fluentMethodName = mapSetterToFluentMethod(setterName);
                    
                    if (fluentMethodName != null) {
                        fluentMethodNames.add(fluentMethodName);
                        argumentsList.add(new ArrayList<>(setterCall.getArguments()));
                        
                        // Delete the old setter call
                        setterCall.delete();
                    }
                }
                
                // Build the fluent chain
                for (int i = 0; i < fluentMethodNames.size(); i++) {
                    CtExecutableReference<?> fluentRef = factory.createExecutableReference();
                    fluentRef.setSimpleName(fluentMethodNames.get(i));
                    fluentRef.setType(fluentConfigType);
                    
                    currentCall = factory.createInvocation(currentCall, fluentRef, argumentsList.get(i));
                }
                
                // Add .load() at the end
                CtExecutableReference<?> loadRef = factory.createExecutableReference();
                loadRef.setSimpleName("load");
                loadRef.setType(flywayType);
                
                CtInvocation<?> loadCall = factory.createInvocation(currentCall, loadRef);
                
                // Replace the constructor call with the new chain
                constructorCall.replace(loadCall);
                
                System.out.println("Migrated Flyway constructor with " + setterCalls.size() + " setters to fluent API");
                
            } catch (Exception e) {
                System.err.println("Error migrating constructor with setters: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        private String mapSetterToFluentMethod(String setterName) {
            switch (setterName) {
                case "setDataSource":
                    return "dataSource";
                case "setClassLoader":
                    return "classLoader";
                case "setLocations":
                    return "locations";
                case "setValidateOnMigrate":
                    return "validateOnMigrate";
                case "setLocationsAsStrings":
                    return "locationsAsStrings";
                default:
                    System.err.println("Warning: Unknown setter method: " + setterName);
                    return null;
            }
        }
    }
}