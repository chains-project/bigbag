package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Generic Flyway API migration transformation for Flyway 8.x to 9.x breaking change.
 * 
 * This transformation handles the following API change:
 * - Old: new Flyway() + setter methods (setDataSource(), setClassLoader(), etc.)
 * - New: Flyway.configure() + fluent configuration + load()
 * 
 * The transformation is generic and can be applied to any Java project
 * by specifying the source directory as a command-line argument.
 */
public class Main {
    
    // Mapping from old setter methods to new fluent configuration methods
    private static final Map<String, String> METHOD_MAPPING = createMethodMapping();
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.err.println("\nThis transformation migrates Flyway 8.x API usage to Flyway 9.x API.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API migration transformation to: " + sourceDir);
        System.out.println("This will transform Flyway 8.x API patterns to Flyway 9.x API");
        
        try {
            // Create Spoon launcher with simplified settings
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(true); // Use no classpath to avoid dependency issues
            launcher.getEnvironment().setCommentEnabled(true);
            
            // Build the model
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Find and transform all Flyway constructor calls
            int transformations = transformFlywayConstructors(model, factory);
            
            System.out.println("\nApplied " + transformations + " transformation(s)");
            
            if (transformations > 0) {
                // Write the transformed code back
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("Transformation completed successfully!");
                System.out.println("\nSummary of changes:");
                System.out.println("1. Replaced 'new Flyway()' with 'Flyway.configure()...load()'");
                System.out.println("2. Converted setter methods to fluent configuration");
                System.out.println("3. Removed old setter method calls");
            } else {
                System.out.println("No Flyway constructor calls found to transform.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformFlywayConstructors(CtModel model, Factory factory) {
        int transformations = 0;
        
        // Find all constructor calls to Flyway class
        List<CtConstructorCall<?>> flywayConstructors = model.getElements(
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructor) {
                    if (constructor.getType() == null) return false;
                    String typeName = constructor.getType().getQualifiedName();
                    return "org.flywaydb.core.Flyway".equals(typeName) || 
                           "Flyway".equals(typeName); // Handle both fully-qualified and simple names
                }
            });
        
        System.out.println("Found " + flywayConstructors.size() + " Flyway constructor(s) to process");
        
        for (CtConstructorCall<?> constructor : flywayConstructors) {
            System.out.println("\nProcessing Flyway constructor at: " + constructor.getPosition());
            
            try {
                // Get the parent statement (should be a variable declaration)
                CtStatement parentStatement = constructor.getParent(CtStatement.class);
                if (!(parentStatement instanceof CtLocalVariable)) {
                    System.err.println("  Warning: Constructor not in local variable declaration, skipping");
                    continue;
                }
                
                CtLocalVariable<?> localVar = (CtLocalVariable<?>) parentStatement;
                String varName = localVar.getSimpleName();
                
                // Get the containing block
                CtBlock<?> block = localVar.getParent(CtBlock.class);
                if (block == null) {
                    System.err.println("  Warning: Cannot find containing block, skipping");
                    continue;
                }
                
                // Find setter method calls on this variable
                List<CtInvocation<?>> setterCalls = findSetterCalls(block, varName, localVar);
                if (setterCalls.isEmpty()) {
                    System.out.println("  No setter method calls found after constructor");
                    continue;
                }
                
                System.out.println("  Found " + setterCalls.size() + " setter method call(s)");
                
                // Build the fluent configuration
                String fluentConfig = buildFluentConfiguration(setterCalls);
                System.out.println("  Generated fluent configuration: " + fluentConfig);
                
                // Replace the constructor call
                CtExpression<?> newExpression = factory.createCodeSnippetExpression(fluentConfig);
                constructor.replace(newExpression);
                
                // Remove the old setter statements
                removeSetterStatements(setterCalls);
                
                transformations++;
                System.out.println("  Successfully transformed");
                
            } catch (Exception e) {
                System.err.println("  Error transforming constructor: " + e.getMessage());
            }
        }
        
        return transformations;
    }
    
    private static List<CtInvocation<?>> findSetterCalls(CtBlock<?> block, String varName, CtLocalVariable<?> localVar) {
        List<CtInvocation<?>> setterCalls = new ArrayList<>();
        List<CtStatement> statements = block.getStatements();
        boolean foundConstructor = false;
        
        for (CtStatement stmt : statements) {
            // Find the constructor statement
            if (stmt.equals(localVar)) {
                foundConstructor = true;
                continue;
            }
            
            if (foundConstructor) {
                // Look for method calls on our variable
                List<CtInvocation<?>> invocations = stmt.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
                for (CtInvocation<?> invocation : invocations) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target != null && target.toString().equals(varName)) {
                        String methodName = invocation.getExecutable().getSimpleName();
                        if (methodName.startsWith("set") && METHOD_MAPPING.containsKey(methodName)) {
                            setterCalls.add(invocation);
                        }
                    }
                }
                
                // If we found any setter calls, continue collecting them
                // If we didn't find any in this statement, we might have reached the end
                // of the configuration block, but let's be conservative and stop
                if (!setterCalls.isEmpty() && invocations.isEmpty()) {
                    break;
                }
            }
        }
        
        return setterCalls;
    }
    
    private static String buildFluentConfiguration(List<CtInvocation<?>> setterCalls) {
        // Check if we have a setClassLoader call - this needs special handling
        String classLoaderArg = null;
        List<CtInvocation<?>> otherSetters = new ArrayList<>();
        
        for (CtInvocation<?> setter : setterCalls) {
            String oldMethodName = setter.getExecutable().getSimpleName();
            if ("setClassLoader".equals(oldMethodName)) {
                List<CtExpression<?>> args = setter.getArguments();
                if (!args.isEmpty()) {
                    classLoaderArg = args.get(0).toString();
                }
            } else {
                otherSetters.add(setter);
            }
        }
        
        // Start with appropriate configure() call
        StringBuilder config = new StringBuilder("Flyway.configure(");
        if (classLoaderArg != null) {
            config.append(classLoaderArg);
        }
        config.append(")");
        
        // Add other configuration methods
        for (CtInvocation<?> setter : otherSetters) {
            String oldMethodName = setter.getExecutable().getSimpleName();
            String newMethodName = METHOD_MAPPING.get(oldMethodName);
            
            if (newMethodName != null) {
                List<CtExpression<?>> args = setter.getArguments();
                if (!args.isEmpty()) {
                    config.append(".").append(newMethodName).append("(");
                    
                    // Handle special cases
                    String arg = args.get(0).toString();
                    if ("setValidateOnMigrate".equals(oldMethodName) && arg.startsWith("Boolean.valueOf(")) {
                        // Extract the boolean value from Boolean.valueOf()
                        arg = arg.substring("Boolean.valueOf(".length(), arg.length() - 1);
                    }
                    
                    config.append(arg).append(")");
                }
            }
        }
        
        config.append(".load()");
        return config.toString();
    }
    
    private static void removeSetterStatements(List<CtInvocation<?>> setterCalls) {
        for (CtInvocation<?> setter : setterCalls) {
            CtStatement stmt = setter.getParent(CtStatement.class);
            if (stmt != null) {
                stmt.delete();
            }
        }
    }
    
    private static Map<String, String> createMethodMapping() {
        Map<String, String> mapping = new HashMap<>();
        
        // Core configuration methods
        mapping.put("setDataSource", "dataSource");
        mapping.put("setLocations", "locations");
        mapping.put("setValidateOnMigrate", "validateOnMigrate");
        // Note: setClassLoader is handled specially - passed to configure() method
        
        // Migration configuration
        mapping.put("setBaselineOnMigrate", "baselineOnMigrate");
        mapping.put("setBaselineVersion", "baselineVersion");
        mapping.put("setTarget", "target");
        mapping.put("setOutOfOrder", "outOfOrder");
        mapping.put("setIgnoreMissingMigrations", "ignoreMissingMigrations");
        mapping.put("setIgnoreIgnoredMigrations", "ignoreIgnoredMigrations");
        mapping.put("setIgnorePendingMigrations", "ignorePendingMigrations");
        mapping.put("setIgnoreFutureMigrations", "ignoreFutureMigrations");
        
        // Clean configuration
        mapping.put("setCleanDisabled", "cleanDisabled");
        mapping.put("setCleanOnValidationError", "cleanOnValidationError");
        
        // General configuration
        mapping.put("setEncoding", "encoding");
        mapping.put("setSchemas", "schemas");
        mapping.put("setTable", "table");
        mapping.put("setMixed", "mixed");
        mapping.put("setGroup", "group");
        
        // Placeholder configuration
        mapping.put("setPlaceholderReplacement", "placeholderReplacement");
        mapping.put("setPlaceholders", "placeholders");
        mapping.put("setPlaceholderPrefix", "placeholderPrefix");
        mapping.put("setPlaceholderSuffix", "placeholderSuffix");
        
        // SQL migration configuration
        mapping.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        mapping.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        mapping.put("setSqlMigrationSeparator", "sqlMigrationSeparator");
        mapping.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        
        // Extension configuration
        mapping.put("setResolvers", "resolvers");
        mapping.put("setSkipDefaultResolvers", "skipDefaultResolvers");
        mapping.put("setCallbacks", "callbacks");
        mapping.put("setSkipDefaultCallbacks", "skipDefaultCallbacks");
        
        return mapping;
    }
}