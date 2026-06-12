package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic Spoon transformation for Flyway API migration from versions < 9.x to 9.18.0+
 * 
 * This transformation handles the breaking change where:
 * - Old: new Flyway() + setters (setDataSource, setClassLoader, etc.)
 * - New: Flyway.configure() + fluent API + .load()
 * 
 * The transformation is generic and can be applied to any Java project.
 */
public class Main {
    
    // Map of old setter method names to new fluent configuration method names
    private static final Map<String, String> METHOD_MAPPINGS = createMethodMappings();
    
    private static Map<String, String> createMethodMappings() {
        Map<String, String> mappings = new HashMap<>();
        // Core configuration methods
        mappings.put("setDataSource", "dataSource");
        mappings.put("setClassLoader", "classLoader");
        mappings.put("setLocations", "locations");
        mappings.put("setValidateOnMigrate", "validateOnMigrate");
        mappings.put("setBaselineVersion", "baselineVersion");
        mappings.put("setBaselineDescription", "baselineDescription");
        mappings.put("setBaselineOnMigrate", "baselineOnMigrate");
        mappings.put("setCleanDisabled", "cleanDisabled");
        mappings.put("setCleanOnValidationError", "cleanOnValidationError");
        mappings.put("setTarget", "target");
        mappings.put("setOutOfOrder", "outOfOrder");
        mappings.put("setIgnoreMigrationPatterns", "ignoreMigrationPatterns");
        mappings.put("setPlaceholderReplacement", "placeholderReplacement");
        mappings.put("setPlaceholders", "placeholders");
        mappings.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        mappings.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        mappings.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        mappings.put("setTable", "table");
        mappings.put("setTablespace", "tablespace");
        mappings.put("setEncoding", "encoding");
        return mappings;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Flyway API Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Transforming: new Flyway() + setters -> Flyway.configure() + fluent API");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        try {
            launcher.buildModel();
            
            // Find all Flyway constructor calls
            List<CtConstructorCall<?>> flywayConstructions = launcher.getModel()
                .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> element) {
                        if (element.getType() == null) return false;
                        String typeName = element.getType().getQualifiedName();
                        return ("org.flywaydb.core.Flyway".equals(typeName) || "Flyway".equals(typeName)) &&
                               element.getArguments().isEmpty();
                    }
                });
            
            System.out.println("\nFound " + flywayConstructions.size() + " Flyway no-arg constructor calls");
            
            int transformed = 0;
            for (CtConstructorCall<?> construction : flywayConstructions) {
                if (transformConstruction(construction)) {
                    transformed++;
                }
            }
            
            System.out.println("\n=== Transformation Summary ===");
            System.out.println("Total Flyway constructions: " + flywayConstructions.size());
            System.out.println("Successfully transformed: " + transformed);
            
            if (transformed > 0) {
                // Output to transformed directory
                String outputDir = sourceDir + "-transformed";
                launcher.setSourceOutputDirectory(outputDir);
                launcher.prettyprint();
                System.out.println("\nTransformed code written to: " + outputDir);
                System.out.println("\nNext steps:");
                System.out.println("1. Review the transformed code");
                System.out.println("2. Ensure imports are correct (may need to add import for ClassicConfiguration)");
                System.out.println("3. Run your build to verify compilation");
            }
            
        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean transformConstruction(CtConstructorCall<?> construction) {
        try {
            // Get position info for logging
            String pos = construction.getPosition() != null ? 
                        "line " + construction.getPosition().getLine() : "unknown";
            
            // Find the Flyway variable
            CtLocalVariable<?> flywayVar = construction.getParent(CtLocalVariable.class);
            if (flywayVar == null) {
                System.out.println("\n[Line " + pos + "] Skipping: Flyway not assigned to a variable");
                return false;
            }
            
            String varName = flywayVar.getSimpleName();
            System.out.println("\n[Line " + pos + "] Processing Flyway variable: " + varName);
            
            // Collect setter calls on this variable
            List<CtInvocation<?>> setters = new ArrayList<>();
            List<String> newConfigs = new ArrayList<>();
            
            // Look in the parent block for method calls
            construction.getParent().getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))
                .stream()
                .filter(inv -> isSetterOnVariable(inv, varName))
                .forEach(setters::add);
            
            if (setters.isEmpty()) {
                System.out.println("  No setters found, using basic configuration");
                applyBasicTransformation(flywayVar, construction);
                return true;
            }
            
            // Build the fluent configuration
            String typeName = construction.getType().getQualifiedName();
            boolean needsFullQualification = "org.flywaydb.core.Flyway".equals(typeName);
            
            StringBuilder config = new StringBuilder();
            config.append(needsFullQualification ? "org.flywaydb.core.Flyway.configure()" : "Flyway.configure()");
            
            for (CtInvocation<?> setter : setters) {
                String oldMethod = setter.getExecutable().getSimpleName();
                if (METHOD_MAPPINGS.containsKey(oldMethod) && setter.getArguments().size() == 1) {
                    String newMethod = METHOD_MAPPINGS.get(oldMethod);
                    String arg = setter.getArguments().get(0).toString();
                    config.append("\n\t\t.").append(newMethod).append("(").append(arg).append(")");
                    newConfigs.add(newMethod);
                }
            }
            
            config.append("\n\t\t.load()");
            
            // Apply the transformation
            flywayVar.setDefaultExpression(
                construction.getFactory().createCodeSnippetExpression(config.toString())
            );
            
            // Remove the old setter statements
            for (CtInvocation<?> setter : setters) {
                CtStatement stmt = setter.getParent(CtStatement.class);
                if (stmt != null) {
                    stmt.delete();
                }
            }
            
            System.out.println("  Transformed with setters: " + newConfigs);
            return true;
            
        } catch (Exception e) {
            System.err.println("  Error during transformation: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean isSetterOnVariable(CtInvocation<?> invocation, String varName) {
        return invocation.getTarget() != null &&
               invocation.getTarget().toString().equals(varName) &&
               invocation.getExecutable().getSimpleName().startsWith("set");
    }
    
    private static void applyBasicTransformation(CtLocalVariable<?> flywayVar, CtConstructorCall<?> construction) {
        String typeName = construction.getType().getQualifiedName();
        boolean needsFullQualification = "org.flywaydb.core.Flyway".equals(typeName);
        
        String newCode = needsFullQualification ?
            "new org.flywaydb.core.Flyway(new org.flywaydb.core.api.configuration.ClassicConfiguration())" :
            "new Flyway(new org.flywaydb.core.api.configuration.ClassicConfiguration())";
        
        flywayVar.setDefaultExpression(
            construction.getFactory().createCodeSnippetExpression(newCode)
        );
    }
}