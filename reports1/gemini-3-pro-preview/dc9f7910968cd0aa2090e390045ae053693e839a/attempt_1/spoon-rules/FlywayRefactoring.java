package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import spoon.reflect.factory.Factory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class FlywayRefactoring {

    /**
     * Processor to migrate Flyway 5.x/6.x code to the Fluent Configuration API.
     * <p>
     * Detects: Flyway f = new Flyway();
     * Transforms to: FluentConfiguration f = Flyway.configure();
     * <p>
     * Updates usages:
     * 1. f.setDataSource(...) -> f.dataSource(...)
     * 2. f.setLocations(...) -> f.locations(...)
     * 3. f.migrate() -> f.load().migrate()
     * 4. passed as arg -> f.load()
     */
    public static class FlywayProcessor extends AbstractProcessor<CtVariable<?>> {

        private static final Set<String> EXECUTION_METHODS = new HashSet<>(Arrays.asList(
            "migrate", "clean", "info", "validate", "baseline", "repair", "undo"
        ));

        private static final Set<String> KNOWN_SETTERS = new HashSet<>(Arrays.asList(
            "setDataSource", "setLocations", "setValidateOnMigrate", 
            "setSchemas", "setTable", "setBaselineOnMigrate", "setEncoding"
        ));

        @Override
        public boolean isToBeProcessed(CtVariable<?> candidate) {
            // 1. Must be a Local Variable or Field
            if (!(candidate instanceof CtLocalVariable) && !(candidate instanceof CtField)) {
                return false;
            }

            // 2. Type Check (Flyway)
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null || !typeRef.getQualifiedName().endsWith("Flyway")) {
                return false;
            }

            // 3. Initializer Check (must be 'new Flyway()')
            CtExpression<?> defaultExpr = candidate.getDefaultExpression();
            if (!(defaultExpr instanceof CtConstructorCall)) {
                return false;
            }
            CtConstructorCall<?> ctorCall = (CtConstructorCall<?>) defaultExpr;
            return ctorCall.getType().getQualifiedName().endsWith("Flyway") 
                   && ctorCall.getArguments().isEmpty();
        }

        @Override
        public void process(CtVariable<?> variable) {
            Factory factory = getFactory();
            
            // --- 1. Update Variable Declaration ---
            
            // Create reference to FluentConfiguration
            CtTypeReference<?> fluentConfigRef = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            
            // Create Flyway.configure() invocation
            CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
            CtInvocation<?> configureCall = factory.Code().createInvocation(
                factory.Code().createTypeAccess(flywayType),
                factory.Method().createReference(flywayType, fluentConfigRef, "configure")
            );

            // Apply changes to declaration
            variable.setType((CtTypeReference) fluentConfigRef);
            variable.setDefaultExpression(configureCall);

            System.out.println("Migrated declaration: " + variable.getSimpleName() + " at line " + variable.getPosition().getLine());

            // --- 2. Update Usages ---
            
            // We collect references first to avoid concurrent modification issues during AST updates
            List<CtVariableReference<?>> references = variable.getReferences();

            for (CtVariableReference<?> ref : references) {
                CtElement parent = ref.getParent();

                // Case A: Method Invocation Target (e.g., f.setDataSource(...))
                if (parent instanceof CtInvocation) {
                    CtInvocation<?> invocation = (CtInvocation<?>) parent;
                    
                    // Check if 'ref' is the target of the invocation (not an argument)
                    if (invocation.getTarget() != null && invocation.getTarget().equals(ref)) {
                        handleMethodInvocation(invocation);
                        continue;
                    }
                }

                // Case B: Passed as argument, returned, or assigned (Value Usage)
                // e.g., doSomething(f) -> doSomething(f.load())
                // We wrap the reference in .load()
                injectLoadCall(ref);
            }
        }

        private void handleMethodInvocation(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();

            if (KNOWN_SETTERS.contains(methodName)) {
                // Transform: f.setDataSource(...) -> f.dataSource(...)
                // 1. Remove 'set' prefix and decapitalize
                String newName = methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
                invocation.getExecutable().setSimpleName(newName);
                
                // Note: The return type of setters changes from void to FluentConfiguration.
                // In Spoon, we generally don't need to explicitly update the invocation's inferred type 
                // unless we are chaining, but strictly speaking, it is now an expression returning an object.
            } 
            else if (EXECUTION_METHODS.contains(methodName)) {
                // Transform: f.migrate() -> f.load().migrate()
                // The target is currently 'f' (which is now a FluentConfiguration).
                // We need to change the target to 'f.load()'.
                
                CtExpression<?> currentTarget = invocation.getTarget(); // 'f'
                
                // Wrap 'f' into 'f.load()'
                CtInvocation<?> loadCall = createLoadInvocation(currentTarget);
                
                // Set the new target
                invocation.setTarget(loadCall);
            }
            // If unknown method, we leave it. It might be object methods (toString) or user error.
        }

        private void injectLoadCall(CtVariableReference<?> ref) {
            // Only inject load() if it hasn't been done already (defensive check)
            // and if it's strictly an expression usage (not part of declaration logic handled above)
            if (ref.getParent() instanceof CtInvocation) {
                CtInvocation<?> parentInv = (CtInvocation<?>) ref.getParent();
                // If this ref is the TARGET of a method we just processed (e.g. f.load()), skip.
                if (parentInv.getExecutable().getSimpleName().equals("load")) {
                    return;
                }
            }

            // Replace 'f' with 'f.load()'
            CtInvocation<?> loadCall = createLoadInvocation(getFactory().Code().createVariableRead(ref, false));
            ref.replace(loadCall);
        }

        private CtInvocation<?> createLoadInvocation(CtExpression<?> target) {
            Factory factory = getFactory();
            CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
            
            return factory.Code().createInvocation(
                target.clone(),
                factory.Method().createReference(target.getType(), flywayType, "load")
            );
        }
    }

    public static void main(String[] args) {
        // Standard Launcher configuration
        Launcher launcher = new Launcher();
        
        // CLI arguments for flexibility
        String inputPath = "/home/kth/Documents/last_transformer/output/dc9f7910968cd0aa2090e390045ae053693e839a/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/dc9f7910968cd0aa2090e390045ae053693e839a/attempt_1/transformed";

        launcher.addInputResource("/home/kth/Documents/last_transformer/output/dc9f7910968cd0aa2090e390045ae053693e839a/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/dc9f7910968cd0aa2090e390045ae053693e839a/attempt_1/transformed");

        // 1. PRESERVE COMMENTS AND FORMATTING
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 2. DEFENSIVE NO-CLASSPATH MODE
        launcher.getEnvironment().setNoClasspath(true);

        // 3. Add Processor
        launcher.addProcessor(new FlywayProcessor());

        System.out.println("Running Flyway Migration Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}