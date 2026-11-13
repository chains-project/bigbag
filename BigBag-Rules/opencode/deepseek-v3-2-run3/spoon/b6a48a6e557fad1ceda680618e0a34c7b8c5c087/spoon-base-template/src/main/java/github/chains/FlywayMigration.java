package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Filter;
import java.util.*;

/**
 * Generic Spoon transformation for migrating from Flyway 8.x to 9.x API.
 * 
 * Transformation rules:
 * 1. Replace `new Flyway()` with `Flyway.configure()`
 * 2. Replace setter calls with fluent API calls
 * 3. Remove setter statements after transformation
 * 4. Handle classLoader via `Flyway.configure(classLoader)` when setClassLoader is used
 */
public class FlywayMigration extends AbstractProcessor<CtConstructorCall<?>> {
    
    private Map<CtVariable<?>, List<CtInvocation<?>>> variableSetters = new HashMap<>();
    private Map<CtVariable<?>, CtExpression<?>> variableClassLoaders = new HashMap<>();
    
    @Override
    public void process(CtConstructorCall<?> constructorCall) {
        // Check if this is a Flyway constructor call
        if (!isFlywayConstructor(constructorCall)) {
            return;
        }
        
        System.out.println("Found Flyway constructor call at: " + 
            constructorCall.getPosition().getLine());
        
        // Find the variable that will hold the Flyway instance
        CtVariable<?> flywayVariable = findAssignedVariable(constructorCall);
        if (flywayVariable == null) {
            System.out.println("Warning: Flyway constructor not assigned to variable");
            return;
        }
        
        System.out.println("Flyway assigned to variable: " + flywayVariable.getSimpleName());
        
        // Find all setter calls on this variable in the same method/block
        findSetterCallsForVariable(flywayVariable, constructorCall);
    }
    
    @Override
    public void processingDone() {
        // After processing all constructor calls, apply transformations
        for (Map.Entry<CtVariable<?>, List<CtInvocation<?>>> entry : variableSetters.entrySet()) {
            CtVariable<?> variable = entry.getKey();
            List<CtInvocation<?>> setters = entry.getValue();
            
            if (!setters.isEmpty()) {
                applyTransformation(variable, setters);
            }
        }
    }
    
    private boolean isFlywayConstructor(CtConstructorCall<?> constructorCall) {
        if (constructorCall.getType() == null || 
            constructorCall.getType().getQualifiedName() == null) {
            return false;
        }
        return constructorCall.getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
    }
    
    private CtVariable<?> findAssignedVariable(CtConstructorCall<?> constructorCall) {
        CtStatement statement = constructorCall.getParent(CtStatement.class);
        
        if (statement instanceof CtLocalVariable) {
            CtLocalVariable<?> localVar = (CtLocalVariable<?>) statement;
            // Check if the constructor call is the initialization of this variable
            if (localVar.getDefaultExpression() == constructorCall) {
                return localVar;
            }
        }
        
        return null;
    }
    
    private void findSetterCallsForVariable(CtVariable<?> variable, CtConstructorCall<?> constructorCall) {
        // Get the method containing the constructor
        CtMethod<?> containingMethod = constructorCall.getParent(CtMethod.class);
        if (containingMethod == null) {
            return;
        }
        
        List<CtInvocation<?>> setters = new ArrayList<>();
        CtExpression<?> classLoaderExpr = null;
        
        // Find all method calls on this variable
        List<CtInvocation<?>> allInvocations = containingMethod.getElements(new Filter<CtInvocation<?>>() {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                if (invocation.getTarget() instanceof CtVariableAccess) {
                    CtVariableAccess<?> targetVar = (CtVariableAccess<?>) invocation.getTarget();
                    return targetVar.getVariable().equals(variable.getReference());
                }
                return false;
            }
        });
        
        // Filter for setter methods and process them
        for (CtInvocation<?> invocation : allInvocations) {
            String methodName = invocation.getExecutable().getSimpleName();
            
            if (methodName.equals("setClassLoader") && invocation.getArguments().size() == 1) {
                classLoaderExpr = invocation.getArguments().get(0);
            } else if (methodName.startsWith("set") && !methodName.equals("setFlywayTelemetryManager")) {
                setters.add(invocation);
            }
        }
        
        if (!setters.isEmpty()) {
            variableSetters.put(variable, setters);
        }
        if (classLoaderExpr != null) {
            variableClassLoaders.put(variable, classLoaderExpr);
        }
    }
    
    private void applyTransformation(CtVariable<?> variable, List<CtInvocation<?>> setters) {
        try {
            // Get the variable declaration
            CtLocalVariable<?> localVar = (CtLocalVariable<?>) variable;
            
            // Build the new fluent API call
            StringBuilder fluentCall = new StringBuilder();
            
            // Start with configure() or configure(classLoader)
            CtExpression<?> classLoaderExpr = variableClassLoaders.get(variable);
            if (classLoaderExpr != null) {
                fluentCall.append("org.flywaydb.core.Flyway.configure(")
                         .append(classLoaderExpr.toString())
                         .append(")");
            } else {
                fluentCall.append("org.flywaydb.core.Flyway.configure()");
            }
            
            // Add fluent method calls for each setter
            for (CtInvocation<?> setter : setters) {
                String methodName = setter.getExecutable().getSimpleName();
                List<CtExpression<?>> args = setter.getArguments();
                
                if (methodName.equals("setDataSource") && args.size() == 1) {
                    fluentCall.append(".dataSource(").append(args.get(0).toString()).append(")");
                } else if (methodName.equals("setLocations") && args.size() == 1) {
                    fluentCall.append(".locations(").append(args.get(0).toString()).append(")");
                } else if (methodName.equals("setValidateOnMigrate") && args.size() == 1) {
                    fluentCall.append(".validateOnMigrate(").append(args.get(0).toString()).append(")");
                } else if (methodName.equals("setClassLoader")) {
                    // Already handled in configure()
                } else {
                    // Generic transformation for other setters
                    String fluentMethod = methodName.substring(3); // Remove "set"
                    fluentMethod = Character.toLowerCase(fluentMethod.charAt(0)) + fluentMethod.substring(1);
                    fluentCall.append(".").append(fluentMethod).append("(");
                    for (int i = 0; i < args.size(); i++) {
                        if (i > 0) fluentCall.append(", ");
                        fluentCall.append(args.get(i).toString());
                    }
                    fluentCall.append(")");
                }
            }
            
            fluentCall.append(".load()");
            
            // Replace the constructor call with the fluent API
            CtCodeSnippetExpression<?> newExpression = 
                getFactory().createCodeSnippetExpression(fluentCall.toString());
            localVar.setDefaultExpression((CtExpression) newExpression);
            
            System.out.println("Transformed Flyway creation for variable: " + variable.getSimpleName());
            
            // Remove the setter statements
            for (CtInvocation<?> setter : setters) {
                CtStatement setterStatement = setter.getParent(CtStatement.class);
                if (setterStatement != null) {
                    setterStatement.delete();
                }
            }
            
            // Remove classLoader setter if it exists
            CtExpression<?> classLoader = variableClassLoaders.get(variable);
            if (classLoader != null) {
                // Find and remove the setClassLoader call
                CtMethod<?> method = variable.getParent(CtMethod.class);
                if (method != null) {
                    List<CtInvocation<?>> classLoaderInvocations = method.getElements(new Filter<CtInvocation<?>>() {
                        @Override
                        public boolean matches(CtInvocation<?> invocation) {
                            return invocation.getExecutable().getSimpleName().equals("setClassLoader") &&
                                   invocation.getTarget() instanceof CtVariableAccess &&
                                   ((CtVariableAccess<?>) invocation.getTarget()).getVariable().equals(variable.getReference());
                        }
                    });
                    
                    for (CtInvocation<?> invocation : classLoaderInvocations) {
                        CtStatement stmt = invocation.getParent(CtStatement.class);
                        if (stmt != null) {
                            stmt.delete();
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error applying transformation: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.FlywayMigration <source-dir> <output-dir>");
            System.err.println("Example: java github.chains.FlywayMigration /path/to/src /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Migrating Flyway API from " + sourceDir + " to " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add our processor
        launcher.addProcessor(new FlywayMigration());
        
        // Set output directory
        launcher.setSourceOutputDirectory(outputDir);
        
        try {
            launcher.run();
            System.out.println("Migration completed successfully!");
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}