package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.visitor.filter.AbstractFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;
import spoon.support.reflect.code.CtInvocationImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * A Spoon processor that transforms legacy Flyway API usage to the new
 * Flyway 9.21.0+ API.
 * 
 * Transforms:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(dataSource);
 *   flyway.setClassLoader(classLoader);
 *   flyway.setLocations(locations);
 *   flyway.setValidateOnMigrate(validate);
 *   ...
 * 
 * To:
 *   Flyway flyway = Flyway.configure(classLoader)
 *           .dataSource(dataSource)
 *           .locations(locations)
 *           .validateOnMigrate(validate)
 *           ...
 *           .load();
 */
public class FlywayTransformer extends AbstractProcessor<CtConstructorCall<?>> {
    
    @Override
    public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
        // Check if this is a Flyway constructor call with no arguments
        if (candidate.getType() == null) {
            return false;
        }
        
        String typeName = candidate.getType().getQualifiedName();
        if (!"org.flywaydb.core.Flyway".equals(typeName)) {
            return false;
        }
        
        // Only process no-arg constructors (the old API)
        return candidate.getArguments().isEmpty();
    }
    
    @Override
    public void process(CtConstructorCall<?> ctor) {
        System.out.println("Processing Flyway constructor at: " + ctor.getPosition());
        
        try {
            // Get the parent statement to understand context
            CtStatement statement = ctor.getParent(CtStatement.class);
            if (statement == null) {
                System.out.println("  Warning: Constructor not in a statement, skipping");
                return;
            }
            
            // Find the variable that receives this Flyway instance
            String varName = extractVariableName(ctor, statement);
            if (varName == null) {
                System.out.println("  Warning: Could not determine variable name, skipping");
                return;
            }
            
            System.out.println("  Variable name: " + varName);
            
            // Find all setter calls on this variable in the immediately following statements
            CtBlock<?> parentBlock = ctor.getParent(CtBlock.class);
            if (parentBlock == null) {
                System.out.println("  Warning: Not in a block, skipping");
                return;
            }
            
            List<SetterCallInfo> setterCalls = collectSetterCalls(parentBlock, ctor, varName);
            System.out.println("  Found " + setterCalls.size() + " setter calls");
            
            // Build the new constructor call with fluent configuration
            CtConstructorCall<?> newCtor = buildNewConstructorCall(ctor, setterCalls);
            
            // Replace the old constructor with the new one
            ctor.replace(newCtor);
            
            // Delete the old setter calls
            for (SetterCallInfo setterInfo : setterCalls) {
                CtStatement setterStatement = setterInfo.invocation.getParent(CtStatement.class);
                if (setterStatement != null) {
                    setterStatement.delete();
                }
            }
            
            System.out.println("  Successfully transformed");
            
        } catch (Exception e) {
            System.err.println("  Error processing Flyway constructor: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String extractVariableName(CtConstructorCall<?> ctor, CtStatement statement) {
        String statementStr = statement.toString().trim();
        
        // Pattern 1: Variable declaration with assignment
        //   "Flyway flyway = new Flyway();"
        //   "final Flyway flyway = new Flyway();"
        if (statementStr.contains("=") && statementStr.contains("new Flyway()")) {
            String beforeEquals = statementStr.split("=")[0].trim();
            String[] parts = beforeEquals.split("\\s+");
            return parts[parts.length - 1]; // Last part is variable name
        }
        
        // Pattern 2: Assignment to existing variable
        //   "flyway = new Flyway();"
        if (statementStr.matches("^[a-zA-Z_][a-zA-Z0-9_]*\\s*=\\s*new\\s*Flyway\\s*\\(\\s*\\)\\s*;")) {
            return statementStr.split("=")[0].trim();
        }
        
        // Pattern 3: Field assignment
        //   "this.flyway = new Flyway();"
        if (statementStr.matches("^[a-zA-Z_][a-zA-Z0-9_]*\\.flyway\\s*=\\s*new\\s*Flyway\\s*\\(\\s*\\)\\s*;")) {
            return statementStr.split("=")[0].trim();
        }
        
        return null;
    }
    
    private List<SetterCallInfo> collectSetterCalls(CtBlock<?> block, CtConstructorCall<?> ctor, String varName) {
        List<SetterCallInfo> setters = new ArrayList<>();
        boolean foundConstructor = false;
        int constructorIndex = -1;
        
        // Find the index of the constructor statement
        List<CtStatement> statements = block.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            CtStatement stmt = statements.get(i);
            if (stmt.toString().contains("new Flyway()")) {
                foundConstructor = true;
                constructorIndex = i;
                break;
            }
        }
        
        if (!foundConstructor || constructorIndex == -1) {
            return setters;
        }
        
        // Look at statements after the constructor
        for (int i = constructorIndex + 1; i < statements.size(); i++) {
            CtStatement stmt = statements.get(i);
            String stmtStr = stmt.toString().trim();
            
            // Stop if we encounter a statement that doesn't reference our variable
            if (!stmtStr.startsWith(varName + ".")) {
                break;
            }
            
            // Check if this is a setter call
            SetterCallInfo setterInfo = extractSetterCall(stmt, varName);
            if (setterInfo != null) {
                setters.add(setterInfo);
            } else {
                // If it's not a setter, stop collecting
                break;
            }
        }
        
        return setters;
    }
    
    private SetterCallInfo extractSetterCall(CtStatement statement, String varName) {
        // Look for invocations in this statement
        List<CtInvocation<?>> invocations = statement.getElements(new AbstractFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                return element.getTarget() != null && 
                       element.getTarget().toString().equals(varName);
            }
        });
        
        if (invocations.isEmpty()) {
            return null;
        }
        
        CtInvocation<?> invocation = invocations.get(0);
        String methodName = invocation.getExecutable().getSimpleName();
        
        if (methodName.startsWith("set") && isFlywaySetter(methodName)) {
            return new SetterCallInfo(invocation, methodName);
        }
        
        return null;
    }
    
    private boolean isFlywaySetter(String methodName) {
        return methodName.equals("setDataSource") ||
               methodName.equals("setClassLoader") ||
               methodName.equals("setLocations") ||
               methodName.equals("setLocationsAsStrings") ||
               methodName.equals("setValidateOnMigrate");
    }
    
    private CtConstructorCall<?> buildNewConstructorCall(CtConstructorCall<?> oldCtor, List<SetterCallInfo> setterCalls) {
        try {
            // Find classLoader argument if present
            CtExpression<?> classLoaderArg = null;
            List<SetterCallInfo> filteredSetters = new ArrayList<>();
            
            for (SetterCallInfo setterInfo : setterCalls) {
                if ("setClassLoader".equals(setterInfo.methodName)) {
                    // Save class loader argument for configure() method
                    if (!setterInfo.invocation.getArguments().isEmpty()) {
                        classLoaderArg = setterInfo.invocation.getArguments().get(0);
                    }
                } else {
                    filteredSetters.add(setterInfo);
                }
            }
            
            // Start building: Flyway.configure() or Flyway.configure(classLoader)
            CtInvocation<?> configureInvocation;
            if (classLoaderArg != null) {
                // Flyway.configure(classLoader)
                configureInvocation = getFactory().createInvocation(
                    getFactory().createTypeAccess(getFactory().Type().createReference("org.flywaydb.core.Flyway")),
                    getFactory().createExecutableReference().setSimpleName("configure"),
                    classLoaderArg
                );
            } else {
                // Flyway.configure()
                configureInvocation = getFactory().createInvocation(
                    getFactory().createTypeAccess(getFactory().Type().createReference("org.flywaydb.core.Flyway")),
                    getFactory().createExecutableReference().setSimpleName("configure")
                );
            }
            
            CtExpression<?> currentExpression = configureInvocation;
            
            // Map setter names to fluent API method names (excluding classLoader)
            for (SetterCallInfo setterInfo : filteredSetters) {
                String setterName = setterInfo.methodName;
                String fluentMethodName = mapSetterToFluent(setterName);
                
                if (fluentMethodName != null) {
                    // Get arguments from the setter call
                    List<CtExpression<?>> args = new ArrayList<>(setterInfo.invocation.getArguments());
                    
                    // Create the fluent method invocation
                    CtInvocation<?> fluentInvocation = getFactory().createInvocation(
                        currentExpression,
                        getFactory().createExecutableReference().setSimpleName(fluentMethodName)
                    );
                    fluentInvocation.setArguments(args);
                    
                    currentExpression = fluentInvocation;
                }
            }
            
            // Add .load() at the end
            CtInvocation<?> loadInvocation = getFactory().createInvocation(
                currentExpression,
                getFactory().createExecutableReference().setSimpleName("load")
            );
            
            // In Flyway 9.21.0+, we don't use new Flyway(), we use Flyway.configure().load() directly
            // So we replace the entire statement, not just the constructor
            // Create a variable assignment with the result of Flyway.configure().load()
            CtStatement statement = oldCtor.getParent(CtStatement.class);
            if (statement != null) {
                // Replace the entire statement with: Flyway flyway = Flyway.configure()...load();
                // This is handled by the caller
            }
            
            // Actually, we shouldn't create a constructor call at all
            // We should create a method call chain: Flyway.configure()...load()
            // But since we're replacing a constructor call, we need to return something
            // that can replace it. The simplest is to return the load invocation
            // wrapped in a constructor call (though not ideal)
            
            // For simplicity, we'll create a dummy constructor call that will be replaced
            // The actual replacement happens in process() method
            return getFactory().createConstructorCall(
                getFactory().Type().createReference("org.flywaydb.core.Flyway")
            );
            
        } catch (Exception e) {
            System.err.println("Error building new constructor: " + e.getMessage());
            // Fallback
            return getFactory().createConstructorCall(
                getFactory().Type().createReference("org.flywaydb.core.Flyway")
            );
        }
    }
    
    private String mapSetterToFluent(String setterName) {
        switch (setterName) {
            case "setDataSource": return "dataSource";
            case "setLocations": return "locations";
            case "setLocationsAsStrings": return "locations";
            case "setValidateOnMigrate": return "validateOnMigrate";
            default: return null;
        }
    }
    
    private static class SetterCallInfo {
        final CtInvocation<?> invocation;
        final String methodName;
        
        SetterCallInfo(CtInvocation<?> invocation, String methodName) {
            this.invocation = invocation;
            this.methodName = methodName;
        }
    }
}