package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            launcher.run();
            
            // Find all constructor calls to Flyway
            List<CtConstructorCall> flywayConstructors = launcher.getModel()
                .getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall constructorCall) {
                        CtTypeReference<?> typeRef = constructorCall.getType();
                        return typeRef != null && 
                               "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName());
                    }
                });
            
            System.out.println("Found " + flywayConstructors.size() + " Flyway constructor calls");
            
            // Map old setter methods to new fluent configuration methods
            Map<String, String> methodMapping = new HashMap<>();
            methodMapping.put("setDataSource", "dataSource");
            methodMapping.put("setLocations", "locations");
            methodMapping.put("setValidateOnMigrate", "validateOnMigrate");
            methodMapping.put("setBaselineOnMigrate", "baselineOnMigrate");
            methodMapping.put("setBaselineVersion", "baselineVersion");
            methodMapping.put("setBaselineDescription", "baselineDescription");
            methodMapping.put("setEncoding", "encoding");
            methodMapping.put("setSchemas", "schemas");
            methodMapping.put("setTable", "table");
            methodMapping.put("setTarget", "target");
            methodMapping.put("setPlaceholders", "placeholders");
            methodMapping.put("setPlaceholderPrefix", "placeholderPrefix");
            methodMapping.put("setPlaceholderSuffix", "placeholderSuffix");
            methodMapping.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
            methodMapping.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
            methodMapping.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
            
            for (CtConstructorCall constructorCall : flywayConstructors) {
                transformFlywayUsage(constructorCall, methodMapping);
            }
            
            // Output transformed code to source directory (in-place transformation)
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Transformation completed successfully");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformFlywayUsage(CtConstructorCall flywayConstructor, Map<String, String> methodMapping) {
        Factory factory = flywayConstructor.getFactory();
        
        // Get the parent block to find subsequent method calls
        CtBlock<?> parentBlock = flywayConstructor.getParent(CtBlock.class);
        if (parentBlock == null) {
            System.out.println("Warning: Flyway constructor not in a block, skipping");
            return;
        }
        
        // Find all method invocations on this Flyway instance after the constructor
        List<CtInvocation> methodCalls = new ArrayList<>();
        List<CtStatement> statementsToRemove = new ArrayList<>();
        
        // Find the statement that contains the constructor
        CtStatement constructorStatement = flywayConstructor.getParent(CtStatement.class);
        if (constructorStatement == null) {
            System.out.println("Warning: Could not find parent statement for Flyway constructor");
            return;
        }
        
        int constructorIndex = parentBlock.getStatements().indexOf(constructorStatement);
        if (constructorIndex == -1) {
            System.out.println("Warning: Constructor statement not found in block");
            return;
        }
        
        // Look for method calls in statements after the constructor
        for (int i = constructorIndex + 1; i < parentBlock.getStatements().size(); i++) {
            CtStatement statement = parentBlock.getStatements().get(i);
            if (statement instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) statement;
                methodCalls.add(invocation);
                statementsToRemove.add(statement);
            } else {
                // Stop at first non-invocation statement
                break;
            }
        }
        
        // Build the replacement code snippet
        StringBuilder replacementCode = new StringBuilder("Flyway.configure(");
        
        // Check if there's a setClassLoader call
        boolean hasClassLoader = false;
        CtExpression<?> classLoaderArg = null;
        
        for (CtInvocation oldInvocation : methodCalls) {
            String oldMethodName = oldInvocation.getExecutable().getSimpleName();
            if ("setClassLoader".equals(oldMethodName)) {
                hasClassLoader = true;
                List<CtExpression<?>> args = oldInvocation.getArguments();
                if (!args.isEmpty()) {
                    classLoaderArg = args.get(0);
                }
                break;
            }
        }
        
        if (hasClassLoader && classLoaderArg != null) {
            replacementCode.append(classLoaderArg.toString()).append(")");
        } else {
            replacementCode.append(")");
        }
        
        for (CtInvocation oldInvocation : methodCalls) {
            String oldMethodName = oldInvocation.getExecutable().getSimpleName();
            
            // Skip setClassLoader as it's handled above
            if ("setClassLoader".equals(oldMethodName)) {
                continue;
            }
            
            String newMethodName = methodMapping.get(oldMethodName);
            
            if (newMethodName == null) {
                System.out.println("Warning: No mapping for method: " + oldMethodName);
                continue;
            }
            
            replacementCode.append(".").append(newMethodName).append("(");
            
            List<CtExpression<?>> args = oldInvocation.getArguments();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    replacementCode.append(", ");
                }
                // Use the pretty-printed argument
                replacementCode.append(args.get(i).toString());
            }
            
            replacementCode.append(")");
        }
        
        replacementCode.append(".load()");
        
        // Replace the constructor call with the new fluent API call
        if (constructorStatement instanceof CtLocalVariable) {
            CtLocalVariable<?> localVar = (CtLocalVariable<?>) constructorStatement;
            localVar.setDefaultExpression(factory.Code().createCodeSnippetExpression(replacementCode.toString()));
        } else if (constructorStatement instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) constructorStatement;
            assignment.setAssignment(factory.Code().createCodeSnippetExpression(replacementCode.toString()));
        } else {
            // For other cases (like return new Flyway().setDataSource(...))
            // Replace the entire expression
            System.out.println("Warning: Complex Flyway usage pattern - using code snippet replacement");
            flywayConstructor.replace(factory.Code().createCodeSnippetExpression(replacementCode.toString()));
        }
        
        // Remove the old method call statements
        for (CtStatement stmt : statementsToRemove) {
            parentBlock.removeStatement(stmt);
        }
        
        System.out.println("Transformed Flyway usage");
    }
}