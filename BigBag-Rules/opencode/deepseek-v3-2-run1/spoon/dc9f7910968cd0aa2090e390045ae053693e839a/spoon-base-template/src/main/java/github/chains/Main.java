package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtInvocationImpl;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            return;
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to Flyway
        List<CtConstructorCall<?>> flywayConstructions = new ArrayList<>();
        
        // Find all elements in the model
        model.getAllTypes().forEach(type -> {
            type.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructor) {
                    return constructor.getType() != null && 
                           constructor.getType().getQualifiedName() != null &&
                           constructor.getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
                }
            }).forEach(flywayConstructions::add);
        });
            
        System.out.println("Found " + flywayConstructions.size() + " Flyway constructor calls");
        
        // Process each Flyway constructor call
        for (CtConstructorCall<?> constructor : flywayConstructions) {
            System.out.println("Processing Flyway constructor at: " + constructor.getPosition());
            transformFlywayConstruction(constructor);
        }
        
        // Save transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        System.out.println("Transformation complete!");
    }
    
    private static void transformFlywayConstruction(CtConstructorCall<?> constructor) {
        // Find the parent method or block
        CtElement parent = constructor.getParent();
        
        // Look for variable declaration or assignment
        if (parent instanceof CtLocalVariable) {
            transformLocalVariable((CtLocalVariable<?>) parent, constructor);
        } else {
            System.out.println("Warning: Flyway constructor not in local variable declaration at " + 
                             constructor.getPosition() + ". Manual review needed.");
        }
    }
    
    private static void transformLocalVariable(CtLocalVariable<?> variableDecl, CtConstructorCall<?> constructor) {
        // Get the variable reference
        CtVariableReference<?> varRef = variableDecl.getReference();
        
        // Find the containing block
        CtBlock<?> block = variableDecl.getParent(CtBlock.class);
        if (block == null) {
            System.out.println("Error: Cannot find containing block for variable at " + variableDecl.getPosition());
            return;
        }
        
        // Get all statements in the block
        List<CtStatement> statements = new ArrayList<>(block.getStatements());
        int varIndex = statements.indexOf(variableDecl);
        if (varIndex < 0) {
            System.out.println("Error: Cannot find variable declaration in block statements");
            return;
        }
        
        // Collect following setter calls
        List<CtInvocation<?>> setterCalls = new ArrayList<>();
        for (int i = varIndex + 1; i < statements.size(); i++) {
            CtStatement stmt = statements.get(i);
            if (stmt instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) stmt;
                if (isSetterCallOnVariable(invocation, varRef)) {
                    setterCalls.add(invocation);
                } else {
                    break; // Stop at first non-setter
                }
            } else {
                break; // Stop at first non-invocation
            }
        }
        
        if (setterCalls.isEmpty()) {
            System.out.println("Warning: No setter calls found after Flyway constructor at " + constructor.getPosition());
            return;
        }
        
        System.out.println("Found " + setterCalls.size() + " setter calls to transform");
        
        // Build the transformation: Flyway.configure()...
        try {
            // Start with Flyway.configure()
            CtTypeReference<?> flywayTypeRef = constructor.getFactory().Type().createReference("org.flywaydb.core.Flyway");
            CtTypeAccess<?> flywayTypeAccess = constructor.getFactory().createTypeAccess(flywayTypeRef);
            
            // Create configure() invocation
            CtExecutableReference<?> configureRef = constructor.getFactory().createExecutableReference();
            configureRef.setSimpleName("configure");
            configureRef.setType(constructor.getFactory().Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
            
            CtInvocation<?> builder = constructor.getFactory().createInvocation(
                flywayTypeAccess,
                configureRef,
                Collections.emptyList()
            );
            
            // Apply each setter as a builder method
            CtExpression<?> currentExpression = builder;
            for (CtInvocation<?> setter : setterCalls) {
                String methodName = setter.getExecutable().getSimpleName();
                String builderMethodName = mapSetterToBuilderMethod(methodName);
                
                if (builderMethodName != null) {
                    CtExecutableReference<?> builderMethodRef = constructor.getFactory().createExecutableReference();
                    builderMethodRef.setSimpleName(builderMethodName);
                    builderMethodRef.setType(constructor.getFactory().Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
                    
                    currentExpression = constructor.getFactory().createInvocation(
                        currentExpression,
                        builderMethodRef,
                        setter.getArguments()
                    );
                } else {
                    System.out.println("Warning: Unknown setter method: " + methodName);
                }
                
                // Delete the original setter call
                setter.delete();
            }
            
            // Add .load() at the end
            CtExecutableReference<?> loadRef = constructor.getFactory().createExecutableReference();
            loadRef.setSimpleName("load");
            loadRef.setType(flywayTypeRef);
            
            CtInvocation<?> finalExpression = constructor.getFactory().createInvocation(
                currentExpression,
                loadRef,
                Collections.emptyList()
            );
            
            // Replace the constructor call in the variable declaration
            variableDecl.getDefaultExpression().replace(finalExpression);
            
            System.out.println("Successfully transformed Flyway construction");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean isSetterCallOnVariable(CtInvocation<?> invocation, CtVariableReference<?> variable) {
        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return false;
        }
        
        // Check if target is a variable read
        if (target instanceof CtVariableRead) {
            return ((CtVariableRead<?>) target).getVariable().equals(variable);
        }
        
        // Check if target is a field read on our variable
        if (target instanceof CtFieldRead) {
            CtExpression<?> fieldTarget = ((CtFieldRead<?>) target).getTarget();
            return fieldTarget instanceof CtVariableRead && 
                   ((CtVariableRead<?>) fieldTarget).getVariable().equals(variable);
        }
        
        return false;
    }
    
    private static String mapSetterToBuilderMethod(String setterName) {
        // Map common setter methods to builder method names
        switch (setterName) {
            case "setDataSource": return "dataSource";
            case "setClassLoader": return "classLoader";
            case "setLocations": return "locations";
            case "setValidateOnMigrate": return "validateOnMigrate";
            case "setBaselineOnMigrate": return "baselineOnMigrate";
            case "setBaselineVersion": return "baselineVersion";
            case "setBaselineVersionAsString": return "baselineVersion";
            case "setEncoding": return "encoding";
            case "setEncodingAsString": return "encoding";
            case "setTable": return "table";
            case "setSchemas": return "schemas";
            case "setTarget": return "target";
            case "setTargetAsString": return "target";
            case "setPlaceholders": return "placeholders";
            case "setCallbacks": return "callbacks";
            case "setCallbacksAsClassNames": return "callbacks";
            case "setResolvers": return "resolvers";
            case "setResolversAsClassNames": return "resolvers";
            case "setSqlMigrationPrefix": return "sqlMigrationPrefix";
            case "setRepeatableSqlMigrationPrefix": return "repeatableSqlMigrationPrefix";
            case "setSqlMigrationSuffixes": return "sqlMigrationSuffixes";
            case "setSqlMigrationSeparator": return "sqlMigrationSeparator";
            case "setIgnoreMigrationPatterns": return "ignoreMigrationPatterns";
            case "setCherryPick": return "cherryPick";
            case "setInstalledBy": return "installedBy";
            case "setCleanDisabled": return "cleanDisabled";
            case "setCleanOnValidationError": return "cleanOnValidationError";
            case "setGroup": return "group";
            case "setMixed": return "mixed";
            case "setOutOfOrder": return "outOfOrder";
            case "setSkipDefaultCallbacks": return "skipDefaultCallbacks";
            case "setSkipDefaultResolvers": return "skipDefaultResolvers";
            case "setValidateMigrationNaming": return "validateMigrationNaming";
            default: 
                // For unknown setters, try removing "set" prefix and decapitalizing
                if (setterName.startsWith("set") && setterName.length() > 3) {
                    return Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);
                }
                return null;
        }
    }
}