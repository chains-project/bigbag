package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Generic transformation for Flyway API migration from version <9.18.0 to 9.18.0+.
 * 
 * Breaking Change Pattern:
 * - Old: new Flyway() followed by setter calls (setDataSource, setClassLoader, setLocations, etc.)
 * - New: Flyway.configure() builder pattern with fluent API ending in .load()
 * 
 * Transformation Rules:
 * 1. Replace "new Flyway()" with "Flyway.configure()"
 * 2. Map setter methods to fluent methods:
 *    - setDataSource(DataSource) -> .dataSource(DataSource)
 *    - setClassLoader(ClassLoader) -> .classLoader(ClassLoader) 
 *    - setLocations(String) -> .locations(String...)
 *    - setValidateOnMigrate(Boolean) -> .validateOnMigrate(boolean)
 * 3. Add .load() at end of fluent chain
 * 
 * This transformation is generic and can be applied to any project affected by
 * the Flyway 9.18.0 API breaking change by changing the input source directory.
 */

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API migration transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addInputResource(sourceDir);
        launcher.buildModel();
        
        CtModel model = launcher.getModel();
        
        // Find all Flyway constructor calls
        List<CtConstructorCall> flywayCtorCalls = new ArrayList<>();
        model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall ctorCall) {
                return ctorCall.getType() != null && 
                       ctorCall.getType().getQualifiedName().equals("org.flywaydb.core.Flyway") &&
                       ctorCall.getArguments().isEmpty();
            }
        }).forEach(flywayCtorCalls::add);
        
        System.out.println("Found " + flywayCtorCalls.size() + " Flyway constructor calls to transform");
        
        // Map of old setter methods to new fluent method names
        Map<String, String> setterToFluentMap = new HashMap<>();
        setterToFluentMap.put("setDataSource", "dataSource");
        setterToFluentMap.put("setClassLoader", "classLoader");
        setterToFluentMap.put("setLocations", "locations");
        setterToFluentMap.put("setLocationsAsStrings", "locations");
        setterToFluentMap.put("setValidateOnMigrate", "validateOnMigrate");
        
        for (CtConstructorCall ctorCall : flywayCtorCalls) {
            transformFlywayInstantiation(ctorCall, setterToFluentMap);
        }
        
        // Write transformed files
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Files written to: " + sourceDir);
    }
    
    private static void transformFlywayInstantiation(CtConstructorCall ctorCall, Map<String, String> setterToFluentMap) {
        // Get the parent statement
        CtStatement parentStatement = ctorCall.getParent(CtStatement.class);
        if (parentStatement == null) return;
        
        // Find variable name if this is part of a variable declaration
        String varName = null;
        CtLocalVariable localVar = ctorCall.getParent(CtLocalVariable.class);
        if (localVar != null && localVar.getType().getQualifiedName().equals("org.flywaydb.core.Flyway")) {
            varName = localVar.getSimpleName();
        } else {
            // Check if it's an assignment
            CtAssignment assignment = ctorCall.getParent(CtAssignment.class);
            if (assignment != null && assignment.getAssigned() instanceof CtVariableAccess) {
                CtVariableAccess varAccess = (CtVariableAccess) assignment.getAssigned();
                varName = varAccess.getVariable().getSimpleName();
            }
        }
        
        if (varName == null) {
            System.err.println("Warning: Could not determine variable name for Flyway constructor at " + ctorCall.getPosition());
            return;
        }
        
        final String finalVarName = varName;
        
        // Find all setter calls on this variable in the same method/block
        List<CtInvocation> setterCalls = new ArrayList<>();
        
        // Get the parent method to scope our search
        CtMethod parentMethod = ctorCall.getParent(CtMethod.class);
        if (parentMethod != null) {
            parentMethod.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation invocation) {
                    if (invocation.getTarget() instanceof CtVariableAccess) {
                        CtVariableAccess varAccess = (CtVariableAccess) invocation.getTarget();
                        return varAccess.getVariable().getSimpleName().equals(finalVarName) &&
                               setterToFluentMap.containsKey(invocation.getExecutable().getSimpleName());
                    }
                    return false;
                }
            }).forEach(setterCalls::add);
        }
        
        if (!setterCalls.isEmpty()) {
            // Build the new fluent API call
            StringBuilder fluentCall = new StringBuilder("Flyway.configure()");
            
            // Process setter calls (they should be in order in the source)
            for (CtInvocation setterCall : setterCalls) {
                String setterName = setterCall.getExecutable().getSimpleName();
                String fluentName = setterToFluentMap.get(setterName);
                
                if (fluentName != null) {
                    // Get arguments
                    StringBuilder args = new StringBuilder();
                    for (int i = 0; i < setterCall.getArguments().size(); i++) {
                        if (i > 0) args.append(", ");
                        args.append(setterCall.getArguments().get(i).toString());
                    }
                    
                    fluentCall.append(".").append(fluentName).append("(").append(args).append(")");
                    
                    // Remove the setter call
                    setterCall.delete();
                }
            }
            
            // Add .load() at the end
            fluentCall.append(".load()");
            
            // Replace the constructor call with the new fluent API
            ctorCall.replace(ctorCall.getFactory().createCodeSnippetExpression(fluentCall.toString()));
        }
    }
}