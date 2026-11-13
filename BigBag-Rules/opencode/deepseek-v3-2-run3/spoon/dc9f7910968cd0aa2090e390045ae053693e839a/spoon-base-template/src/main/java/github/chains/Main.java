package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.*;

/**
 * Generic transformation rule for migrating from Flyway 8.x to 9.x API.
 * 
 * Transforms:
 *   Old pattern: Flyway flyway = new Flyway();
 *                flyway.setDataSource(dataSource);
 *                flyway.setClassLoader(classLoader);
 *                ... other setters
 *                
 *   New pattern: Flyway flyway = Flyway.configure()
 *                                   .dataSource(dataSource)
 *                                   .classLoader(classLoader)
 *                                   ... other fluent calls
 *                                   .load();
 *                                   
 * This transformation is generic and reusable for any project affected by
 * the Flyway 9.x breaking API change.
 */
public class Main {
    
    // Mapping of old setter method names to new fluent API method names
    private static final Map<String, String> METHOD_MAPPING = createMethodMapping();
    
    private static Map<String, String> createMethodMapping() {
        Map<String, String> map = new HashMap<>();
        
        // Common configuration methods
        map.put("setDataSource", "dataSource");
        map.put("setClassLoader", "classLoader");
        map.put("setLocations", "locations");
        map.put("setValidateOnMigrate", "validateOnMigrate");
        map.put("setBaselineOnMigrate", "baselineOnMigrate");
        map.put("setBaselineVersion", "baselineVersion");
        map.put("setBaselineDescription", "baselineDescription");
        map.put("setPlaceholders", "placeholders");
        map.put("setPlaceholderPrefix", "placeholderPrefix");
        map.put("setPlaceholderSuffix", "placeholderSuffix");
        map.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        map.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        map.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        map.put("setTable", "table");
        map.put("setTarget", "target");
        map.put("setOutOfOrder", "outOfOrder");
        map.put("setCleanOnValidationError", "cleanOnValidationError");
        map.put("setCleanDisabled", "cleanDisabled");
        map.put("setValidateOnMigrate", "validateOnMigrate");
        
        return map;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API migration transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all Flyway instantiations
        List<CtNewClass> flywayInstantiations = model.getElements(new TypeFilter<CtNewClass>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass newClass) {
                CtTypeReference typeRef = newClass.getType();
                return typeRef != null && 
                       "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName());
            }
        });
        
        System.out.println("Found " + flywayInstantiations.size() + " Flyway instantiations to transform");
        
        int totalTransformations = 0;
        
        // Process each Flyway instantiation
        for (CtNewClass flywayNewClass : flywayInstantiations) {
            try {
                if (transformFlywayInstantiation(flywayNewClass)) {
                    totalTransformations++;
                }
            } catch (Exception e) {
                System.err.println("Error transforming Flyway instantiation at " + 
                                 flywayNewClass.getPosition() + ": " + e.getMessage());
            }
        }
        
        // Save the transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete! Total transformations: " + totalTransformations);
        System.out.println("The transformation has:");
        System.out.println("1. Replaced 'new Flyway()' with 'Flyway.configure()'");
        System.out.println("2. Converted setter calls to fluent API calls");
        System.out.println("3. Added '.load()' to create the Flyway instance");
        System.out.println("4. Removed the old setter statements");
        System.out.println("\nThis generic transformation can be applied to ANY project using Flyway 8.x API.");
        System.out.println("\nTo apply to a different project, simply run:");
        System.out.println("  java -jar spoon-transformation.jar /path/to/project/src");
    }
    
    private static boolean transformFlywayInstantiation(CtNewClass flywayNewClass) {
        // Get the parent block
        CtBlock parentBlock = flywayNewClass.getParent(CtBlock.class);
        if (parentBlock == null) {
            return false;
        }
        
        // Get the variable declaration
        CtLocalVariable flywayVar = flywayNewClass.getParent(CtLocalVariable.class);
        if (flywayVar == null) {
            return false;
        }
        
        String varName = flywayVar.getSimpleName();
        System.out.println("Transforming Flyway variable: " + varName);
        
        // Find all statements in the block
        List<CtStatement> statements = new ArrayList<>(parentBlock.getStatements());
        int varIndex = statements.indexOf(flywayVar);
        
        if (varIndex == -1) {
            return false;
        }
        
        // Collect configuration calls
        List<CtInvocation> configCalls = new ArrayList<>();
        List<CtStatement> toRemove = new ArrayList<>();
        
        for (int i = varIndex + 1; i < statements.size(); i++) {
            CtStatement stmt = statements.get(i);
            if (stmt instanceof CtInvocation) {
                CtInvocation invoc = (CtInvocation) stmt;
                CtExpression target = invoc.getTarget();
                
                if (target instanceof CtVariableAccess) {
                    CtVariableAccess varAccess = (CtVariableAccess) target;
                    if (varName.equals(varAccess.getVariable().getSimpleName())) {
                        String methodName = invoc.getExecutable().getSimpleName();
                        if (METHOD_MAPPING.containsKey(methodName)) {
                            configCalls.add(invoc);
                            toRemove.add(stmt);
                        }
                    }
                }
            }
        }
        
        if (configCalls.isEmpty()) {
            System.out.println("  No configuration calls found for " + varName);
            return false;
        }
        
        System.out.println("  Found " + configCalls.size() + " configuration calls");
        
        // Build the new expression
        StringBuilder newExpr = new StringBuilder();
        newExpr.append("org.flywaydb.core.Flyway.configure()");
        
        for (CtInvocation call : configCalls) {
            String oldMethod = call.getExecutable().getSimpleName();
            String newMethod = METHOD_MAPPING.get(oldMethod);
            
            if (newMethod != null) {
                newExpr.append(".").append(newMethod).append("(");
                
                List<CtExpression> args = call.getArguments();
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) newExpr.append(", ");
                    newExpr.append(args.get(i).toString());
                }
                
                newExpr.append(")");
            }
        }
        
        newExpr.append(".load()");
        
        System.out.println("  New expression: " + newExpr.toString());
        
        // Create and set the new expression
        CtCodeSnippetExpression newInit = flywayVar.getFactory().createCodeSnippetExpression(newExpr.toString());
        flywayVar.setDefaultExpression(newInit);
        
        // Remove old statements
        for (CtStatement stmt : toRemove) {
            parentBlock.removeStatement(stmt);
        }
        
        return true;
    }
}