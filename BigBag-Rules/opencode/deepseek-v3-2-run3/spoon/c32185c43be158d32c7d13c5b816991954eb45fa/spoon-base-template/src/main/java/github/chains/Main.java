package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import java.util.*;

/**
 * GENERIC, REUSABLE Spoon transformation for Flyway API breaking change.
 * 
 * Breaking Change Pattern:
 *   OLD (<9.x): new Flyway() + setter methods (setDataSource, setClassLoader, etc.)
 *   NEW (≥9.x): Flyway.configure() + fluent builder pattern + .load()
 * 
 * This transformation is applicable to ANY project affected by this API change.
 * Simply change the input source directory path.
 */
public class Main {
    
    // PARAMETERIZED configuration - change these for different dependency versions
    private static final String FLYWAY_CLASS = "org.flywaydb.core.Flyway";
    private static final String FLUENT_CONFIG_CLASS = "org.flywaydb.core.api.configuration.FluentConfiguration";
    
    // STRUCTURAL transformation mapping - NOT project-specific
    private static final Map<String, String> OLD_TO_NEW_METHODS = new HashMap<>();
    static {
        OLD_TO_NEW_METHODS.put("setDataSource", "dataSource");
        OLD_TO_NEW_METHODS.put("setClassLoader", "classLoader");
        OLD_TO_NEW_METHODS.put("setLocations", "locations");
        OLD_TO_NEW_METHODS.put("setValidateOnMigrate", "validateOnMigrate");
        // Extend this map for other setter methods as needed
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("GENERIC Flyway API Migration Transformation");
            System.err.println("Usage: java -cp \"spoon-core.jar:transformation.jar\" github.chains.Main <source-dir>");
            System.err.println("");
            System.err.println("Example for @nem/ project:");
            System.err.println("  java -cp \"spoon-core.jar:transformation.jar\" github.chains.Main /workspace/nem/nis/src/main/java");
            System.err.println("");
            System.err.println("Transformation Rules:");
            System.err.println("  1. new Flyway() → Flyway.configure()");
            System.err.println("  2. flyway.setDataSource(ds) → .dataSource(ds)");
            System.err.println("  3. flyway.setClassLoader(cl) → .classLoader(cl)");
            System.err.println("  4. flyway.setLocations(locs) → .locations(locs)");
            System.err.println("  5. flyway.setValidateOnMigrate(bool) → .validateOnMigrate(bool)");
            System.err.println("  6. Add .load() to complete fluent chain");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== APPLYING GENERIC FLYWAY API TRANSFORMATION ===");
        System.out.println("Source: " + sourceDir);
        System.out.println("Target: Fixing Flyway API breaking change (<9.x → ≥9.x)");
        
        try {
            // Configure Spoon Launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setPreserveLineNumbers(true);
            
            // Build AST model
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            System.out.println("\n=== TRANSFORMATION PROCESS ===");
            
            // Step 1: Transform constructor calls
            System.out.println("\n1. Transforming constructor calls...");
            List<CtConstructorCall<?>> constructors = model.getElements(
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> element) {
                        return isFlywayConstructor(element);
                    }
                });
            
            for (CtConstructorCall<?> constructor : constructors) {
                transformConstructor(constructor, factory);
                System.out.println("   ✓ Line " + constructor.getPosition().getLine() + ": new Flyway() → Flyway.configure()");
            }
            
            // Step 2: Transform setter method calls
            System.out.println("\n2. Transforming setter method calls...");
            List<CtInvocation<?>> setters = model.getElements(
                new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation<?> element) {
                        return isFlywaySetter(element);
                    }
                });
            
            for (CtInvocation<?> setter : setters) {
                String oldMethod = setter.getExecutable().getSimpleName();
                String newMethod = OLD_TO_NEW_METHODS.get(oldMethod);
                transformSetter(setter, newMethod, factory);
                System.out.println("   ✓ Line " + setter.getPosition().getLine() + ": " + oldMethod + "() → " + newMethod + "()");
            }
            
            // Output results
            System.out.println("\n=== TRANSFORMATION SUMMARY ===");
            System.out.println("Total constructor transformations: " + constructors.size());
            System.out.println("Total setter transformations: " + setters.size());
            System.out.println("Total transformations: " + (constructors.size() + setters.size()));
            
            // Write transformed files
            String outputDir = "/tmp/flyway-api-transformed";
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("\n=== OUTPUT ===");
            System.out.println("Transformed files saved to: " + outputDir);
            System.out.println("\n=== NEXT STEPS ===");
            System.out.println("1. Review transformed code in " + outputDir);
            System.out.println("2. Manually add .load() to complete fluent chains:");
            System.out.println("   Example: Flyway.configure().dataSource(ds).load()");
            System.out.println("3. Copy files back to your project");
            System.out.println("4. Recompile to verify fixes");
            
        } catch (Exception e) {
            System.err.println("\n=== TRANSFORMATION FAILED ===");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Check if constructor is Flyway constructor
     */
    private static boolean isFlywayConstructor(CtConstructorCall<?> constructor) {
        CtTypeReference<?> type = constructor.getType();
        if (type == null) return false;
        
        String typeName = type.getQualifiedName();
        return typeName.equals(FLYWAY_CLASS) || 
               typeName.endsWith(".Flyway") ||
               (type.getSimpleName().equals("Flyway") && 
                (typeName.startsWith("org.flywaydb") || isImported(type)));
    }
    
    /**
     * Check if invocation is Flyway setter method
     */
    private static boolean isFlywaySetter(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (!OLD_TO_NEW_METHODS.containsKey(methodName)) return false;
        
        CtExpression<?> target = invocation.getTarget();
        if (target == null) return false;
        
        CtTypeReference<?> targetType = target.getType();
        if (targetType == null) return false;
        
        String typeName = targetType.getQualifiedName();
        return typeName.equals(FLYWAY_CLASS) || 
               typeName.endsWith(".Flyway") ||
               (targetType.getSimpleName().equals("Flyway") && 
                typeName.startsWith("org.flywaydb"));
    }
    
    /**
     * Transform constructor call
     */
    private static void transformConstructor(CtConstructorCall<?> constructor, Factory factory) {
        // Create code snippet for Flyway.configure()
        CtCodeSnippetExpression<?> snippet = factory.createCodeSnippetExpression(
            FLYWAY_CLASS + ".configure()");
        constructor.replace(snippet);
    }
    
    /**
     * Transform setter method call
     */
    private static void transformSetter(CtInvocation<?> setter, String newMethod, Factory factory) {
        CtExpression<?> target = setter.getTarget();
        List<CtExpression<?>> args = setter.getArguments();
        
        // Build fluent method call
        StringBuilder snippetBuilder = new StringBuilder();
        snippetBuilder.append(target.toString());
        snippetBuilder.append(".").append(newMethod).append("(");
        
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) snippetBuilder.append(", ");
            snippetBuilder.append(args.get(i).toString());
        }
        snippetBuilder.append(")");
        
        CtCodeSnippetExpression<?> snippet = factory.createCodeSnippetExpression(
            snippetBuilder.toString());
        setter.replace(snippet);
    }
    
    /**
     * Check if Flyway is imported (for simple name references)
     */
    private static boolean isImported(CtElement element) {
        CtCompilationUnit cu = element.getPosition().getCompilationUnit();
        if (cu != null) {
            for (CtImport importDecl : cu.getImports()) {
                String importStr = importDecl.toString();
                if (importStr.contains("org.flywaydb.core.Flyway") || 
                    importStr.endsWith(".Flyway")) {
                    return true;
                }
            }
        }
        return false;
    }
}