package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic transformation rule for migrating from mutable builder pattern 
 * to fluent builder pattern in library APIs.
 * 
 * Pattern: Transforms code from:
 *   Type obj = new Type();
 *   obj.setX(a);
 *   obj.setY(b);
 *   return obj;
 * 
 * To:
 *   Type obj = Type.configure()
 *       .x(a)
 *       .y(b)
 *       .load();
 *   return obj;
 * 
 * This handles breaking changes where libraries move from mutable setters
 * to immutable fluent builders.
 */
public class Main {
    
    /**
     * Configuration for a specific library migration.
     */
    static class MigrationConfig {
        final String oldClassName;
        final String factoryMethod; // e.g., "configure", "builder", etc.
        final String terminalMethod; // e.g., "load", "build", "create"
        final Map<String, String> methodRenames; // old method name -> new method name
        
        MigrationConfig(String oldClassName, String factoryMethod, String terminalMethod) {
            this.oldClassName = oldClassName;
            this.factoryMethod = factoryMethod;
            this.terminalMethod = terminalMethod;
            this.methodRenames = new HashMap<>();
        }
        
        MigrationConfig withMethodRename(String oldName, String newName) {
            methodRenames.put(oldName, newName);
            return this;
        }
    }
    
    // Configure known migrations
    private static final Map<String, MigrationConfig> MIGRATIONS = new HashMap<>();
    
    static {
        // Flyway 8.x -> 9.x migration
        MigrationConfig flywayMigration = new MigrationConfig(
            "org.flywaydb.core.Flyway", 
            "configure", 
            "load"
        );
        // Flyway method renames (setX -> x)
        flywayMigration.withMethodRename("setDataSource", "dataSource");
        flywayMigration.withMethodRename("setClassLoader", "classLoader");
        flywayMigration.withMethodRename("setLocations", "locations");
        flywayMigration.withMethodRename("setValidateOnMigrate", "validateOnMigrate");
        
        MIGRATIONS.put("org.flywaydb.core.Flyway", flywayMigration);
        
        // Add more library migrations here as needed
        // Example for another library:
        // MigrationConfig otherLib = new MigrationConfig(
        //     "com.example.Library",
        //     "builder",
        //     "build"
        // );
        // MIGRATIONS.put("com.example.Library", otherLib);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Available migrations: " + String.join(", ", MIGRATIONS.keySet()));
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        int totalTransformations = 0;
        
        // Apply each configured migration
        for (MigrationConfig config : MIGRATIONS.values()) {
            int transformations = applyMigration(model, config, launcher.getFactory());
            totalTransformations += transformations;
            System.out.println("Applied " + transformations + " transformations for " + config.oldClassName);
        }
        
        if (totalTransformations > 0) {
            // Apply changes
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Successfully applied " + totalTransformations + " transformations!");
        } else {
            System.out.println("No transformations were applied.");
        }
    }
    
    private static int applyMigration(CtModel model, MigrationConfig config, spoon.reflect.factory.Factory factory) {
        int transformations = 0;
        
        // Find all constructor calls for the target class
        List<CtConstructorCall<?>> constructors = new ArrayList<>();
        for (CtType<?> type : model.getAllTypes()) {
            List<CtConstructorCall<?>> typeConstructors = Query.getElements(type, 
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> element) {
                        return element.getType() != null && 
                               config.oldClassName.equals(element.getType().getQualifiedName());
                    }
                });
            constructors.addAll(typeConstructors);
        }
        
        System.out.println("Found " + constructors.size() + " constructor calls for " + config.oldClassName);
        
        for (CtConstructorCall<?> constructor : constructors) {
            if (transformInstance(constructor, config, factory)) {
                transformations++;
            }
        }
        
        return transformations;
    }
    
    private static boolean transformInstance(CtConstructorCall<?> constructor, 
                                           MigrationConfig config,
                                           spoon.reflect.factory.Factory factory) {
        try {
            CtMethod<?> method = constructor.getParent(CtMethod.class);
            if (method == null) return false;
            
            String varName = findVariableName(constructor);
            if (varName == null) return false;
            
            System.out.println("Transforming " + config.oldClassName + " instance: " + varName);
            
            List<CtInvocation<?>> invocations = findMethodCallsOnVariable(method, varName);
            
            return applyTransformation(constructor, invocations, config, factory);
            
        } catch (Exception e) {
            System.err.println("Error transforming instance: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean applyTransformation(CtConstructorCall<?> constructor,
                                             List<CtInvocation<?>> invocations,
                                             MigrationConfig config,
                                             spoon.reflect.factory.Factory factory) {
        
        // Build the fluent API chain
        StringBuilder fluentChain = new StringBuilder();
        
        // Start with ClassName.factoryMethod()
        String className = constructor.getType().getSimpleName();
        fluentChain.append(className).append(".").append(config.factoryMethod).append("()");
        
        // Add each method call as fluent API call
        for (CtInvocation<?> invocation : invocations) {
            String methodName = invocation.getExecutable().getSimpleName();
            String fluentMethodName = config.methodRenames.getOrDefault(methodName, 
                toDefaultFluentName(methodName));
            
            // Build arguments
            StringBuilder args = new StringBuilder();
            List<CtExpression<?>> arguments = invocation.getArguments();
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) args.append(", ");
                args.append(arguments.get(i).toString());
            }
            
            fluentChain.append(".").append(fluentMethodName).append("(").append(args).append(")");
            
            // Remove the original method call
            CtStatement parentStmt = invocation.getParent(CtStatement.class);
            if (parentStmt != null) {
                parentStmt.delete();
            }
        }
        
        // Add terminal method at the end
        fluentChain.append(".").append(config.terminalMethod).append("()");
        
        // Replace the constructor call with the fluent chain
        CtCodeSnippetExpression newExpression = factory.createCodeSnippetExpression(fluentChain.toString());
        
        // Apply the replacement
        CtVariable<?> variable = constructor.getParent(CtVariable.class);
        if (variable != null) {
            variable.setDefaultExpression(newExpression);
            return true;
        } else {
            CtAssignment<?, ?> assignment = constructor.getParent(CtAssignment.class);
            if (assignment != null) {
                assignment.setAssignment(newExpression);
                return true;
            } else {
                constructor.replace(newExpression);
                return true;
            }
        }
    }
    
    private static String toDefaultFluentName(String oldName) {
        // Default transformation: setXyz -> xyz
        if (oldName.startsWith("set")) {
            String baseName = oldName.substring(3);
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        }
        return oldName;
    }
    
    private static String findVariableName(CtConstructorCall<?> constructor) {
        CtVariable<?> variable = constructor.getParent(CtVariable.class);
        if (variable != null) {
            return variable.getSimpleName();
        }
        
        CtAssignment<?, ?> assignment = constructor.getParent(CtAssignment.class);
        if (assignment != null) {
            CtExpression<?> assigned = assignment.getAssigned();
            if (assigned instanceof CtVariableRead) {
                CtVariableRead<?> varRead = (CtVariableRead<?>) assigned;
                return varRead.getVariable().getSimpleName();
            }
        }
        
        return null;
    }
    
    private static List<CtInvocation<?>> findMethodCallsOnVariable(CtMethod<?> method, String varName) {
        List<CtInvocation<?>> result = new ArrayList<>();
        
        if (method.getBody() == null) return result;
        
        List<CtInvocation<?>> allInvocations = Query.getElements(method.getBody(), 
            new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : allInvocations) {
            CtExpression<?> target = invocation.getTarget();
            if (target instanceof CtVariableRead) {
                CtVariableRead<?> varRead = (CtVariableRead<?>) target;
                if (varName.equals(varRead.getVariable().getSimpleName())) {
                    result.add(invocation);
                }
            }
        }
        
        result.sort(Comparator.comparingInt(inv -> inv.getPosition().getLine()));
        return result;
    }
    
    private static void printUsage() {
        System.err.println("Usage: java github.chains.Main <source-directory>");
        System.err.println();
        System.err.println("This tool applies automatic fixes for breaking API changes in libraries.");
        System.err.println("Currently configured migrations:");
        for (Map.Entry<String, MigrationConfig> entry : MIGRATIONS.entrySet()) {
            MigrationConfig config = entry.getValue();
            System.err.println("  - " + config.oldClassName + ": " + 
                config.factoryMethod + "() -> ... -> " + config.terminalMethod + "()");
        }
    }
}