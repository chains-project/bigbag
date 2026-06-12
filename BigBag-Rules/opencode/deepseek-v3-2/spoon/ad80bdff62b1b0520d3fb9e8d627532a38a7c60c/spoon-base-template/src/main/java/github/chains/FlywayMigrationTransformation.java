package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation rule for migrating from Flyway 8.x to 9.x API.
 * 
 * Breaking Change Pattern:
 * - Old API: new Flyway() + setters (setDataSource, setClassLoader, setLocations, etc.)
 * - New API: Flyway.configure() or Flyway.configure(ClassLoader) + builder pattern + load()
 * 
 * Transformation Rules:
 * 1. new Flyway() -> Flyway.configure() or Flyway.configure(classLoader) if setClassLoader is used
 * 2. setDataSource(ds) -> .dataSource(ds)
 * 3. setLocations(locs) -> .locations(locs)  
 * 4. setValidateOnMigrate(bool) -> .validateOnMigrate(bool)
 * 5. setClassLoader(cl) -> Pass to configure() method instead
 * 6. Chain all builder methods and end with .load()
 * 
 * This transformation is generic and can be applied to any project affected by
 * the Flyway 9.x API breaking change.
 */
public class FlywayMigrationTransformation {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java FlywayMigrationTransformation <source-directory>");
            System.err.println("Example: java FlywayMigrationTransformation /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transforming Flyway 8.x API to 9.x API...");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            launcher.buildModel();
            
            // Find all Flyway constructor calls
            List<CtConstructorCall> flywayCtors = launcher.getModel()
                .getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall element) {
                        CtTypeReference typeRef = element.getType();
                        return typeRef != null && 
                               "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName()) &&
                               element.getArguments().isEmpty(); // no-args constructor
                    }
                });
            
            System.out.println("Found " + flywayCtors.size() + " Flyway constructor calls");
            int transformedCount = 0;
            
            for (CtConstructorCall ctorCall : flywayCtors) {
                System.out.println("\nProcessing Flyway constructor at: " + ctorCall.getPosition());
                
                CtMethod parentMethod = ctorCall.getParent(CtMethod.class);
                if (parentMethod == null) {
                    System.out.println("  Warning: Could not find parent method, skipping");
                    continue;
                }
                
                // Find variable name for Flyway instance
                String varName = findFlywayVariableName(ctorCall);
                final String finalVarName = varName;
                
                // Find all setter calls on this Flyway instance
                List<CtInvocation> setterCalls = parentMethod.getElements(
                    new TypeFilter<CtInvocation>(CtInvocation.class) {
                        @Override
                        public boolean matches(CtInvocation element) {
                            CtExpression target = element.getTarget();
                            return target != null && 
                                   target.toString().equals(finalVarName) &&
                                   element.getExecutable().getSimpleName().startsWith("set");
                        }
                    }
                );
                
                // Sort by line number to maintain order
                setterCalls.sort((a, b) -> a.getPosition().getLine() - b.getPosition().getLine());
                
                // Check if setClassLoader is used
                boolean hasClassLoaderSetter = setterCalls.stream()
                    .anyMatch(inv -> "setClassLoader".equals(inv.getExecutable().getSimpleName()));
                
                // Build transformation
                String transformation = buildTransformation(setterCalls, hasClassLoaderSetter);
                
                // Apply transformation
                Factory factory = launcher.getFactory();
                CtExpression newExpr = factory.createCodeSnippetExpression(transformation);
                ctorCall.replace(newExpr);
                
                // Delete old setter calls
                for (CtInvocation setterCall : setterCalls) {
                    setterCall.delete();
                }
                
                System.out.println("  Transformed: " + transformation);
                transformedCount++;
            }
            
            System.out.println("\n=== Transformation Summary ===");
            System.out.println("Total Flyway constructors found: " + flywayCtors.size());
            System.out.println("Successfully transformed: " + transformedCount);
            System.out.println("Failed: " + (flywayCtors.size() - transformedCount));
            
            // Output transformed code
            String outputDir = sourceDir + "-transformed";
            System.out.println("\nWriting transformed files to: " + outputDir);
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("\nTransformation complete!");
            System.out.println("Note: Review imports - you may need to add:");
            System.out.println("  import org.flywaydb.core.Flyway;");
            System.out.println("  (if not already present)");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static String findFlywayVariableName(CtConstructorCall ctorCall) {
        CtLocalVariable localVar = ctorCall.getParent(CtLocalVariable.class);
        if (localVar != null) {
            return localVar.getSimpleName();
        }
        return "flyway"; // Default name if we can't determine
    }
    
    private static String buildTransformation(List<CtInvocation> setterCalls, boolean hasClassLoaderSetter) {
        StringBuilder sb = new StringBuilder();
        
        // Start with configure() - with or without ClassLoader
        if (hasClassLoaderSetter) {
            // Find the setClassLoader call to get its argument
            CtInvocation classLoaderCall = setterCalls.stream()
                .filter(inv -> "setClassLoader".equals(inv.getExecutable().getSimpleName()))
                .findFirst()
                .orElse(null);
            
            if (classLoaderCall != null && !classLoaderCall.getArguments().isEmpty()) {
                sb.append("Flyway.configure(").append(classLoaderCall.getArguments().get(0)).append(")");
            } else {
                sb.append("Flyway.configure()");
            }
        } else {
            sb.append("Flyway.configure()");
        }
        
        // Add other builder methods
        for (CtInvocation setterCall : setterCalls) {
            String setterName = setterCall.getExecutable().getSimpleName();
            
            if ("setClassLoader".equals(setterName)) {
                continue; // Already handled in configure()
            }
            
            String builderMethod = mapSetterToBuilder(setterName);
            if (builderMethod != null) {
                sb.append(".").append(builderMethod).append("(");
                List<CtExpression> args = setterCall.getArguments();
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(args.get(i));
                }
                sb.append(")");
            }
        }
        
        // End with load()
        sb.append(".load()");
        
        return sb.toString();
    }
    
    private static String mapSetterToBuilder(String setterName) {
        switch (setterName) {
            case "setDataSource": return "dataSource";
            case "setLocations": return "locations";
            case "setValidateOnMigrate": return "validateOnMigrate";
            case "setBaselineOnMigrate": return "baselineOnMigrate";
            case "setBaselineVersion": return "baselineVersion";
            case "setBaselineDescription": return "baselineDescription";
            case "setEncoding": return "encoding";
            case "setTable": return "table";
            case "setTarget": return "target";
            case "setSchemas": return "schemas";
            case "setPlaceholders": return "placeholders";
            case "setCallbacks": return "callbacks";
            case "setResolvers": return "resolvers";
            case "setSkipDefaultCallbacks": return "skipDefaultCallbacks";
            case "setSkipDefaultResolvers": return "skipDefaultResolvers";
            case "setOutOfOrder": return "outOfOrder";
            case "setCleanOnValidationError": return "cleanOnValidationError";
            case "setCleanDisabled": return "cleanDisabled";
            case "setMixed": return "mixed";
            case "setGroup": return "group";
            case "setIgnoreMissingMigrations": return "ignoreMissingMigrations";
            case "setIgnoreIgnoredMigrations": return "ignoreIgnoredMigrations";
            case "setIgnoreFutureMigrations": return "ignoreFutureMigrations";
            case "setValidateMigrationNaming": return "validateMigrationNaming";
            case "setPlaceholderPrefix": return "placeholderPrefix";
            case "setPlaceholderSuffix": return "placeholderSuffix";
            case "setPlaceholderSeparator": return "placeholderSeparator";
            case "setPlaceholderReplacement": return "placeholderReplacement";
            case "setSqlMigrationPrefix": return "sqlMigrationPrefix";
            case "setRepeatableSqlMigrationPrefix": return "repeatableSqlMigrationPrefix";
            case "setSqlMigrationSeparator": return "sqlMigrationSeparator";
            case "setSqlMigrationSuffixes": return "sqlMigrationSuffixes";
            case "setJavaMigrationClassProvider": return "javaMigrationClassProvider";
            case "setJavaMigrations": return "javaMigrations";
            default: return null;
        }
    }
}