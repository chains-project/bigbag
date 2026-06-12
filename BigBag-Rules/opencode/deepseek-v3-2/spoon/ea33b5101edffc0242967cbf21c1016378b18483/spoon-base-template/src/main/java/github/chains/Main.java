package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation to fix Flyway API breaking change from version 8.x to 9.x.
 * 
 * Old API pattern: new Flyway() -> flyway.setXxx(...) method calls
 * New API pattern: Flyway.configure() -> fluentConfiguration.xxx(...) -> .load()
 * 
 * This transformation:
 * 1. Finds all constructor calls to org.flywaydb.core.Flyway
 * 2. Maps setter methods to fluent API methods (setDataSource -> dataSource, etc.)
 * 3. Handles setClassLoader specially (passes it to configure() method)
 * 4. Chains all configuration methods and ends with .load()
 * 
 * Usage: java github.chains.Main <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            // Create model
            CtModel model = launcher.buildModel();
            
            // Find all constructor calls to Flyway
            List<CtConstructorCall<?>> flywayConstructors = new ArrayList<>();
            for (CtType<?> type : model.getAllTypes()) {
                flywayConstructors.addAll(type.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> constructorCall) {
                        return constructorCall.getType() != null && 
                               constructorCall.getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
                    }
                }));
            }
            
            System.out.println("Found " + flywayConstructors.size() + " Flyway constructor calls");
            
            boolean modified = false;
            for (CtConstructorCall<?> constructorCall : flywayConstructors) {
                if (transformFlywayInstantiation(constructorCall)) {
                    modified = true;
                }
            }
            
            if (modified) {
                // Apply transformations
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("Transformation complete! Files have been updated.");
            } else {
                System.out.println("No transformations were needed.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Transforms a single Flyway instantiation from old API to new API.
     * 
     * @param constructorCall The constructor call to transform
     * @return true if transformation was successful, false otherwise
     */
    private static boolean transformFlywayInstantiation(CtConstructorCall<?> constructorCall) {
        System.out.println("Processing Flyway constructor at: " + constructorCall.getPosition());
        
        // Find the parent variable declaration
        CtLocalVariable<?> variableDecl = constructorCall.getParent(CtLocalVariable.class);
        if (variableDecl == null) {
            System.out.println("  Warning: Flyway constructor not in variable declaration, skipping");
            return false;
        }
        
        String varName = variableDecl.getSimpleName();
        System.out.println("  Transforming Flyway variable: " + varName);
        
        // Collect all setter invocations on this variable
        List<CtInvocation<?>> setterInvocations = new ArrayList<>();
        CtStatement parentStmt = constructorCall.getParent(CtStatement.class);
        
        if (parentStmt == null) {
            System.out.println("  Warning: Cannot find parent statement");
            return false;
        }
        
        // Get all statements in the parent block
        List<CtStatement> statements = parentStmt.getParent().getElements(new TypeFilter<CtStatement>(CtStatement.class));
        int constructorIndex = statements.indexOf(parentStmt);
        
        if (constructorIndex == -1) {
            System.out.println("  Warning: Cannot find constructor in statement list");
            return false;
        }
        
        // Look for setter calls in statements after the constructor
        for (int i = constructorIndex + 1; i < statements.size(); i++) {
            CtStatement stmt = statements.get(i);
            List<CtInvocation<?>> invocations = stmt.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
            
            boolean foundSetter = false;
            for (CtInvocation<?> inv : invocations) {
                if (inv.getExecutable().getSimpleName().startsWith("set") && 
                    inv.getTarget() instanceof CtVariableAccess) {
                    CtVariableAccess<?> varAccess = (CtVariableAccess<?>) inv.getTarget();
                    if (varAccess.getVariable().getSimpleName().equals(varName)) {
                        setterInvocations.add(inv);
                        foundSetter = true;
                        System.out.println("    Found setter: " + inv.getExecutable().getSimpleName());
                    }
                }
            }
            
            // Stop if we didn't find a setter on this statement
            if (!foundSetter) {
                break;
            }
        }
        
        // Build the new fluent API code
        StringBuilder newCode = new StringBuilder();
        
        // Check for setClassLoader to determine if we need configure(ClassLoader)
        boolean hasClassLoader = false;
        String classLoaderArg = null;
        for (CtInvocation<?> inv : setterInvocations) {
            if (inv.getExecutable().getSimpleName().equals("setClassLoader")) {
                hasClassLoader = true;
                classLoaderArg = inv.getArguments().get(0).toString();
                break;
            }
        }
        
        // Start with Flyway.configure() or Flyway.configure(classLoader)
        newCode.append("org.flywaydb.core.Flyway.configure(");
        if (hasClassLoader && classLoaderArg != null) {
            newCode.append(classLoaderArg);
        }
        newCode.append(")");
        
        // Add fluent configuration methods for other setters
        for (CtInvocation<?> inv : setterInvocations) {
            String setterName = inv.getExecutable().getSimpleName();
            if (setterName.equals("setClassLoader")) {
                continue; // Already handled in configure()
            }
            
            // Convert setXxx to xxx (camelCase)
            String fluentMethod = setterName.substring(3);
            fluentMethod = fluentMethod.substring(0, 1).toLowerCase() + fluentMethod.substring(1);
            
            newCode.append("\n\t\t.").append(fluentMethod).append("(");
            
            // Add arguments
            for (int i = 0; i < inv.getArguments().size(); i++) {
                if (i > 0) newCode.append(", ");
                newCode.append(inv.getArguments().get(i).toString());
            }
            
            newCode.append(")");
        }
        
        // End with .load()
        newCode.append("\n\t\t.load()");
        
        System.out.println("  Generated new code pattern");
        
        try {
            // Replace the constructor call with the new expression
            variableDecl.setDefaultExpression(
                constructorCall.getFactory().Code().createCodeSnippetExpression(newCode.toString())
            );
            
            // Remove the setter statements
            for (CtInvocation<?> inv : setterInvocations) {
                CtStatement stmt = inv.getParent(CtStatement.class);
                if (stmt != null) {
                    stmt.delete();
                }
            }
            
            return true;
        } catch (Exception e) {
            System.out.println("  Error during transformation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Maps a setter method name to the corresponding fluent API method name.
     * This handles common Flyway setters and provides a generic fallback.
     */
    private static String mapSetterToFluentMethod(String setterName) {
        switch (setterName) {
            case "setDataSource": return "dataSource";
            case "setLocations": return "locations";
            case "setValidateOnMigrate": return "validateOnMigrate";
            case "setClassLoader": return null; // Handled specially in configure()
            default:
                // Generic conversion: setPropertyName -> propertyName
                if (setterName.startsWith("set") && setterName.length() > 3) {
                    String property = setterName.substring(3);
                    return property.substring(0, 1).toLowerCase() + property.substring(1);
                }
                return null;
        }
    }
}