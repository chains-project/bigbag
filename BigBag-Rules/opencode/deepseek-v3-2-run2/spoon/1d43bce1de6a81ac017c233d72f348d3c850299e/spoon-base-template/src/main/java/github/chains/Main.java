package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.*;

/**
 * Generic Spoon transformation for Flyway API migration from pre-9.15.0 to 9.15.0+.
 * 
 * Transforms old pattern:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(dataSource);
 *   flyway.setClassLoader(classLoader);
 *   flyway.setLocations(locations);
 *   flyway.setValidateOnMigrate(validate);
 * 
 * To new pattern:
 *   Flyway flyway = Flyway.configure([classLoader])
 *                       .dataSource(dataSource)
 *                       .locations(locations)
 *                       .validateOnMigrate(validate)
 *                       .load();
 * 
 * This transformation is generic and can be applied to any project.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to org.flywaydb.core.Flyway
        List<CtConstructorCall<?>> flywayCtors = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                return element.getType() != null && 
                       "org.flywaydb.core.Flyway".equals(element.getType().getQualifiedName());
            }
        });
        
        System.out.println("Found " + flywayCtors.size() + " Flyway constructor call(s)");
        
        int transformed = 0;
        for (CtConstructorCall<?> ctor : flywayCtors) {
            if (transformFlywayInstantiation(ctor)) {
                transformed++;
            }
        }
        
        if (transformed > 0) {
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Successfully transformed " + transformed + " Flyway instantiation(s)");
        } else {
            System.out.println("No transformations applied (no matching patterns found)");
        }
    }
    
    /**
     * Transforms a Flyway constructor call to the new fluent API pattern.
     */
    private static boolean transformFlywayInstantiation(CtConstructorCall<?> ctor) {
        try {
            // Get the enclosing method and variable name
            String varName = getVariableName(ctor);
            if (varName == null) {
                System.out.println("  Skipping: Could not determine variable name");
                return false;
            }
            
            CtMethod<?> enclosingMethod = ctor.getParent(CtMethod.class);
            if (enclosingMethod == null) {
                System.out.println("  Skipping: Not in a method");
                return false;
            }
            
            System.out.println("  Processing variable '" + varName + "' in " + 
                             enclosingMethod.getSimpleName() + "()");
            
            // Find all setter calls on this variable
            Map<String, CtExpression<?>> setters = findSetters(enclosingMethod, varName);
            System.out.println("  Found " + setters.size() + " setter(s)");
            
            // Build the new fluent API expression
            StringBuilder newExpr = new StringBuilder();
            
            // Start with configure() with or without ClassLoader
            if (setters.containsKey("ClassLoader")) {
                newExpr.append("Flyway.configure(");
                newExpr.append(setters.get("ClassLoader").toString());
                newExpr.append(")");
                setters.remove("ClassLoader");
            } else {
                newExpr.append("Flyway.configure()");
            }
            
            // Add dataSource if present
            if (setters.containsKey("DataSource")) {
                newExpr.append(".dataSource(");
                newExpr.append(setters.get("DataSource").toString());
                newExpr.append(")");
            }
            
            // Add locations if present (convert to varargs)
            if (setters.containsKey("Locations")) {
                newExpr.append(".locations(");
                newExpr.append(setters.get("Locations").toString());
                newExpr.append(")");
            }
            
            // Add validateOnMigrate if present (convert Boolean to boolean)
            if (setters.containsKey("ValidateOnMigrate")) {
                newExpr.append(".validateOnMigrate(");
                String validateExpr = setters.get("ValidateOnMigrate").toString();
                // Handle Boolean.booleanValue() if present
                if (validateExpr.endsWith(".booleanValue()")) {
                    validateExpr = validateExpr.substring(0, validateExpr.length() - 15);
                }
                newExpr.append(validateExpr);
                newExpr.append(")");
            }
            
            // Add other setters using lowercase-first-letter convention
            for (Map.Entry<String, CtExpression<?>> entry : setters.entrySet()) {
                String property = entry.getKey();
                String fluentMethod = Character.toLowerCase(property.charAt(0)) + property.substring(1);
                newExpr.append(".").append(fluentMethod).append("(");
                newExpr.append(entry.getValue().toString());
                newExpr.append(")");
            }
            
            // End with .load()
            newExpr.append(".load()");
            
            System.out.println("  New expression: " + newExpr.toString());
            
            // Replace the constructor call with the new expression
            CtCodeSnippetExpression<?> snippet = ctor.getFactory().createCodeSnippetExpression(newExpr.toString());
            ctor.replace(snippet);
            
            // Delete all the setter statements
            deleteSetterStatements(enclosingMethod, varName);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("  Error transforming: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Extracts the variable name from a constructor call context.
     */
    private static String getVariableName(CtConstructorCall<?> ctor) {
        CtElement parent = ctor.getParent();
        while (parent != null) {
            if (parent instanceof CtLocalVariable) {
                return ((CtLocalVariable<?>) parent).getSimpleName();
            } else if (parent instanceof CtAssignment) {
                CtExpression<?> assigned = ((CtAssignment<?, ?>) parent).getAssigned();
                if (assigned instanceof CtVariableAccess) {
                    return ((CtVariableAccess<?>) assigned).getVariable().getSimpleName();
                }
            }
            parent = parent.getParent();
        }
        return null;
    }
    
    /**
     * Finds all setter method calls on a given variable within a method.
     */
    private static Map<String, CtExpression<?>> findSetters(CtMethod<?> method, String varName) {
        Map<String, CtExpression<?>> setters = new HashMap<>();
        List<CtInvocation<?>> invocations = method.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        for (CtInvocation<?> inv : invocations) {
            if (isSetterOnVariable(inv, varName)) {
                String methodName = inv.getExecutable().getSimpleName();
                if (methodName.startsWith("set") && inv.getArguments().size() == 1) {
                    String property = methodName.substring(3); // Remove "set"
                    setters.put(property, inv.getArguments().get(0));
                }
            }
        }
        
        return setters;
    }
    
    /**
     * Checks if an invocation is a setter call on the specified variable.
     */
    private static boolean isSetterOnVariable(CtInvocation<?> invocation, String varName) {
        if (invocation.getTarget() instanceof CtVariableAccess) {
            CtVariableAccess<?> target = (CtVariableAccess<?>) invocation.getTarget();
            return varName.equals(target.getVariable().getSimpleName());
        }
        return false;
    }
    
    /**
     * Deletes all setter statements for a given variable.
     */
    private static void deleteSetterStatements(CtMethod<?> method, String varName) {
        List<CtInvocation<?>> invocations = method.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        List<CtStatement> toDelete = new ArrayList<>();
        
        for (CtInvocation<?> inv : invocations) {
            if (isSetterOnVariable(inv, varName)) {
                String methodName = inv.getExecutable().getSimpleName();
                if (methodName.startsWith("set")) {
                    CtStatement stmt = inv.getParent(CtStatement.class);
                    if (stmt != null) {
                        toDelete.add(stmt);
                    }
                }
            }
        }
        
        for (CtStatement stmt : toDelete) {
            stmt.delete();
        }
    }
}