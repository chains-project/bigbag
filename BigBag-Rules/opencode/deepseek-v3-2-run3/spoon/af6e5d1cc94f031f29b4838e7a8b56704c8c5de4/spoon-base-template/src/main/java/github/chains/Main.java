package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic transformation for Flyway API migration.
 * 
 * BREAKING CHANGE: Flyway 9+ removed no-arg constructor and setters from Flyway class.
 * 
 * OLD PATTERN (pre-9):
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(dataSource);
 *   flyway.setClassLoader(classLoader);
 *   flyway.setLocations(locations);
 *   flyway.setValidateOnMigrate(validate);
 * 
 * NEW PATTERN (9+):
 *   Flyway flyway = Flyway.configure()
 *       .dataSource(dataSource)
 *       .classLoader(classLoader) 
 *       .locations(locations)
 *       .validateOnMigrate(validate)
 *       .load();
 * 
 * This transformation:
 * 1. Finds all no-arg Flyway constructor calls
 * 2. For each, finds associated setter calls on the same variable
 * 3. Transforms to fluent API pattern
 * 4. Is generic - works on any Java project with this pattern
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source_directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Flyway API Migration Transformation ===");
        System.out.println("Processing: " + sourceDir);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setLevel("INFO");
            
            // Build the model
            System.out.println("Building code model...");
            launcher.buildModel();
            
            // Apply transformation
            int transformations = applyTransformation(launcher);
            
            if (transformations > 0) {
                System.out.println("\nWriting transformed code...");
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("Successfully transformed " + transformations + " Flyway usage(s)");
            } else {
                System.out.println("\nNo Flyway usages found to transform");
            }
            
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int applyTransformation(Launcher launcher) {
        int transformationCount = 0;
        
        // Find all methods in the codebase
        List<CtMethod<?>> allMethods = launcher.getModel().getElements(new TypeFilter<>(CtMethod.class));
        
        for (CtMethod<?> method : allMethods) {
            CtBlock<?> body = method.getBody();
            if (body == null) continue;
            
            // Find Flyway constructor calls in this method
            List<CtConstructorCall<?>> flywayConstructors = findFlywayConstructors(body);
            
            for (CtConstructorCall<?> constructor : flywayConstructors) {
                if (transformFlywayUsage(constructor, method)) {
                    transformationCount++;
                }
            }
        }
        
        return transformationCount;
    }
    
    private static List<CtConstructorCall<?>> findFlywayConstructors(CtBlock<?> block) {
        List<CtConstructorCall<?>> constructors = new ArrayList<>();
        
        // Get all constructor calls in the block
        List<CtConstructorCall<?>> allConstructors = block.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall<?> constructor : allConstructors) {
            CtTypeReference<?> type = constructor.getType();
            if (type != null && 
                "org.flywaydb.core.Flyway".equals(type.getQualifiedName()) &&
                constructor.getArguments().isEmpty()) {
                constructors.add(constructor);
            }
        }
        
        return constructors;
    }
    
    private static boolean transformFlywayUsage(CtConstructorCall<?> constructor, CtMethod<?> method) {
        try {
            System.out.println("\nFound Flyway constructor at: " + constructor.getPosition());
            
            // Get the variable that will hold the Flyway instance
            String varName = extractVariableName(constructor);
            if (varName == null) {
                System.out.println("  Skipping: Could not determine variable name");
                return false;
            }
            
            System.out.println("  Variable: " + varName);
            
            // Find all setter calls on this variable in the method
            List<CtInvocation<?>> setterCalls = findSetterCalls(method, varName);
            
            if (setterCalls.isEmpty()) {
                System.out.println("  No setter calls found - using minimal transformation");
            } else {
                System.out.println("  Found " + setterCalls.size() + " setter call(s)");
            }
            
            // Create the transformed code
            String transformedCode = buildFluentChain(varName, setterCalls, constructor.getFactory());
            
            // Replace the constructor with the transformed code
            CtCodeSnippetExpression<?> snippet = constructor.getFactory()
                .createCodeSnippetExpression(transformedCode);
            constructor.replace(snippet);
            
            // Delete the old setter calls
            for (CtInvocation<?> setter : setterCalls) {
                setter.delete();
            }
            
            System.out.println("  Successfully transformed");
            return true;
            
        } catch (Exception e) {
            System.out.println("  Transformation failed: " + e.getMessage());
            return false;
        }
    }
    
    private static String extractVariableName(CtConstructorCall<?> constructor) {
        CtElement parent = constructor.getParent();
        
        // Case 1: Variable declaration with initialization
        if (parent instanceof CtVariable) {
            return ((CtVariable<?>) parent).getSimpleName();
        }
        
        // Case 2: Assignment to variable
        if (parent instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parent;
            CtExpression<?> lhs = assignment.getAssigned();
            if (lhs instanceof CtVariableWrite) {
                return ((CtVariableWrite<?>) lhs).getVariable().getSimpleName();
            }
        }
        
        return null;
    }
    
    private static List<CtInvocation<?>> findSetterCalls(CtMethod<?> method, String varName) {
        List<CtInvocation<?>> setters = new ArrayList<>();
        
        List<CtInvocation<?>> allInvocations = method.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : allInvocations) {
            String methodName = invocation.getExecutable().getSimpleName();
            
            // Check if it's a relevant setter method
            if (!isFlywaySetter(methodName)) {
                continue;
            }
            
            // Check if target is our variable
            CtExpression<?> target = invocation.getTarget();
            if (target instanceof CtVariableAccess) {
                CtVariableAccess<?> varAccess = (CtVariableAccess<?>) target;
                if (varName.equals(varAccess.getVariable().getSimpleName())) {
                    setters.add(invocation);
                }
            }
        }
        
        // Sort by line number to maintain execution order
        setters.sort(Comparator.comparingInt(inv -> inv.getPosition().getLine()));
        
        return setters;
    }
    
    private static boolean isFlywaySetter(String methodName) {
        return methodName.startsWith("set") && 
               !methodName.equals("setFlywayTelemetryManager") &&
               (methodName.equals("setDataSource") ||
                methodName.equals("setClassLoader") ||
                methodName.equals("setLocations") ||
                methodName.equals("setLocationsAsStrings") ||
                methodName.equals("setValidateOnMigrate"));
    }
    
    private static String buildFluentChain(String varName, List<CtInvocation<?>> setterCalls, Factory factory) {
        StringBuilder chain = new StringBuilder();
        
        // Start the chain
        chain.append("Flyway.configure()");
        
        // Add each configuration method
        for (CtInvocation<?> setter : setterCalls) {
            String methodName = setter.getExecutable().getSimpleName();
            String fluentMethod = mapToFluentMethod(methodName);
            
            if (fluentMethod == null) {
                continue; // Skip unknown setters
            }
            
            chain.append(".").append(fluentMethod).append("(");
            
            // Add arguments
            List<CtExpression<?>> args = setter.getArguments();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) chain.append(", ");
                
                // For simplicity, we use the original code as string
                // This preserves the actual argument expressions
                chain.append(args.get(i).toString());
            }
            
            chain.append(")");
        }
        
        // End the chain
        chain.append(".load()");
        
        return chain.toString();
    }
    
    private static String mapToFluentMethod(String setterName) {
        switch (setterName) {
            case "setDataSource": return "dataSource";
            case "setClassLoader": return "classLoader";
            case "setLocations": return "locations";
            case "setLocationsAsStrings": return "locationsAsStrings";
            case "setValidateOnMigrate": return "validateOnMigrate";
            default: return null;
        }
    }
}