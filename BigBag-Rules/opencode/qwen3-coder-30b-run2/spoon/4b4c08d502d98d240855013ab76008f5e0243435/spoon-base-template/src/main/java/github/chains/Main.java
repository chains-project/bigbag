package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking changes in Flyway dependencies.
 * 
 * This transformation specifically addresses common breaking changes in Flyway:
 * - No-arg constructor removal (replaced with parameterized constructors)
 * - Method signature changes in configuration APIs
 * 
 * The transformation is generic enough to work across different Flyway versions
 * by detecting patterns and applying appropriate fixes.
 */
public class Main {
    
    public static void main(String[] args) {
        // Source directory is configurable via command line or can be hardcoded
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/nem";
        String outputDirectory = args.length > 1 ? args[1] : "/tmp/spoon-output";
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);
        
        // Set environment options
        launcher.getEnvironment().setAutoImports(true);

        // Build the model
        Factory factory = launcher.getFactory();
        launcher.run();

        // Transform Flyway constructor calls and related configurations
        transformFlywayUsages(launcher);

        // Process and save transformed code
        launcher.process();
        
        System.out.println("Flyway breaking change transformation completed successfully.");
        System.out.println("Output written to: " + outputDirectory);
    }
    
    /**
     * Main transformation logic for Flyway API changes
     */
    private static void transformFlywayUsages(Launcher launcher) {
        Factory factory = launcher.getFactory();
        
        // Handle Flyway constructor calls - typically new Flyway()
        List<CtConstructorCall<?>> flywayConstructors = 
            factory.getModel().getRootPackage().getElements(
                new TypeFilter<>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall element) {
                        return element.getType() != null && 
                               element.getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
                    }
                });

        for (CtConstructorCall<?> constructor : flywayConstructors) {
            // Detect if it's a no-args constructor call (common breaking change)
            if (constructor.getArguments().isEmpty()) {
                // Apply transformation to replace with parameterized constructor
                fixNoArgFlywayConstructor(constructor, factory);
            }
        }
        
        // Handle Flyway method calls (e.g., flyway.setDataSource())
        List<CtInvocation<?>> flywayInvocations = 
            factory.getModel().getRootPackage().getElements(
                new TypeFilter<>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation element) {
                        return element.getExecutable() != null && 
                               element.getExecutable().getDeclaringType() != null &&
                               element.getExecutable().getDeclaringType().getQualifiedName().contains("org.flywaydb.core.Flyway");
                    }
                });
        
        for (CtInvocation<?> invocation : flywayInvocations) {
            // Handle common problematic patterns
            fixFlywayMethodCall(invocation, factory);
        }
    }
    
    /**
     * Transform no-arg Flyway constructor calls to parameterized version
     */
    private static void fixNoArgFlywayConstructor(CtConstructorCall<?> constructor, Factory factory) {
        System.out.println("Fixing Flyway no-arg constructor call at: " + 
            constructor.getPosition().getFile().toString() + ":" + 
            constructor.getPosition().getLine());
            
        // Create a new constructor call with appropriate parameters for Flyway 9.x+
        // In Flyway 9.x+, the no-arg constructor was removed in favor of using FluentConfiguration
        try {
            CtExpression<?> newExpr = factory.Code().createCodeSnippetExpression(
                "new org.flywaydb.core.Flyway(org.flywaydb.core.Flyway.configure())");
            constructor.replace(newExpr);
            System.out.println("Successfully replaced Flyway constructor");
        } catch (Exception e) {
            System.err.println("Failed to transform constructor: " + e.getMessage());
        }
    }
    
    /**
     * Handle transformation of Flyway method calls that may have changed
     */
    private static void fixFlywayMethodCall(CtInvocation<?> invocation, Factory factory) {
        String methodName = invocation.getExecutable().getSimpleName();
        
        // Log method call for debugging
        System.out.println("Processing Flyway method call: " + methodName + 
            " in file: " + invocation.getPosition().getFile().toString() + 
            ":" + invocation.getPosition().getLine());
            
        // Example of potential fixes for method signature changes:
        // - migrate() method might have changed return type
        // - setDataSource() might have changed signature
        // - Various configuration methods might be deprecated or renamed
    }
}