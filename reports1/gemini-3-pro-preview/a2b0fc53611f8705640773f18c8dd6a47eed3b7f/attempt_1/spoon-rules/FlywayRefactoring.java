package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtLocalVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Refactoring rule for Flyway 5 -> 6/7 Migration.
 * 
 * CHANGES HANDLED:
 * 1. Default constructor 'new Flyway()' removed.
 * 2. 'Flyway' class no longer holds configuration (setDataSource, etc. removed).
 * 3. Configuration moved to 'ClassicConfiguration' (or FluentConfiguration).
 * 
 * STRATEGY:
 * 1. Detect 'Flyway f = new Flyway();'
 * 2. Convert variable type to 'ClassicConfiguration f'.
 * 3. Convert constructor to 'new ClassicConfiguration()'.
 * 4. This preserves validity of existing setters (e.g., f.setDataSource(...)) which now exist on ClassicConfiguration.
 * 5. Wrap execution methods (migrate, clean, etc.) which don't exist on Config:
 *    'f.migrate()' -> 'new Flyway(f).migrate()'.
 */
public class FlywayRefactoring {

    public static class FlywayProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        
        // Methods that perform actions and require a Flyway instance (not present on Configuration)
        private static final Set<String> EXECUTION_METHODS = new HashSet<>(Arrays.asList(
            "migrate", "clean", "info", "validate", "baseline", "repair", "undo"
        ));

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // Defensive check for NoClasspath
            CtTypeReference<?> type = candidate.getType();
            if (type == null) return false;
            
            // Check for constructor: new Flyway()
            // We use string matching to be safe in NoClasspath environments
            if (!type.getQualifiedName().endsWith(".Flyway")) return false;
            
            // Only target the no-arg constructor (which was removed)
            return candidate.getArguments().isEmpty();
        }

        @Override
        public void process(CtConstructorCall<?> ctorCall) {
            Factory factory = getFactory();
            
            // Structural Check: Ensure this constructor is part of a local variable declaration.
            // e.g., "Flyway flyway = new Flyway();"
            CtElement parent = ctorCall.getParent();
            if (!(parent instanceof CtLocalVariable)) {
                // If the constructor is used loosely (not assigned), we skip to avoid complex flow analysis issues
                return;
            }

            CtLocalVariable<?> variable = (CtLocalVariable<?>) parent;
            String variableName = variable.getSimpleName();

            // Define Type References
            CtTypeReference<?> oldFlywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
            CtTypeReference<?> classicConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.ClassicConfiguration");

            // STEP 1: Update the Variable Type
            // From: Flyway f ...
            // To:   ClassicConfiguration f ...
            variable.setType((CtTypeReference) classicConfigType);

            // STEP 2: Update the Constructor Call
            // From: ... = new Flyway();
            // To:   ... = new ClassicConfiguration();
            ctorCall.getExecutable().setDeclaringType((CtTypeReference) classicConfigType);
            ctorCall.setType((CtTypeReference) classicConfigType);

            // STEP 3: Process usages of this variable
            // We scan the enclosing block to find where 'f' is used.
            CtElement scope = variable.getParent(); 
            List<CtInvocation<?>> invocations = scope.getElements(new TypeFilter<>(CtInvocation.class));

            for (CtInvocation<?> invocation : invocations) {
                CtExpression<?> target = invocation.getTarget();
                
                // Defensive: check if target matches our variable name
                if (target instanceof CtVariableAccess && 
                    ((CtVariableAccess<?>) target).getVariable().getSimpleName().equals(variableName)) {

                    String methodName = invocation.getExecutable().getSimpleName();

                    // If it is an Execution Method (migrate, clean), it no longer exists on ClassicConfiguration.
                    // We must create a temporary Flyway instance: new Flyway(config).migrate()
                    if (EXECUTION_METHODS.contains(methodName)) {
                        
                        // Create: new Flyway(variable)
                        CtConstructorCall<?> newFlywayWrapper = factory.Code().createConstructorCall(
                            oldFlywayType,
                            factory.Code().createVariableRead(variable.getReference(), false)
                        );

                        // Replace the target of the invocation
                        // Before: variable.migrate()
                        // After:  new Flyway(variable).migrate()
                        invocation.setTarget(newFlywayWrapper);
                        
                        System.out.println(" [Refactor] Wrapped execution method '" + methodName + "' at line " + invocation.getPosition().getLine());
                    }
                    // Else: It is a Configuration Method (setDataSource, setLocations).
                    // These methods exist on ClassicConfiguration (per the Diff), so the call remains valid
                    // on the new variable type. No action needed.
                }
            }
            
            System.out.println(" [Refactor] Converted 'new Flyway()' to 'new ClassicConfiguration()' at line " + ctorCall.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Standard Launcher Setup
        String inputPath = "/home/kth/Documents/last_transformer/output/a2b0fc53611f8705640773f18c8dd6a47eed3b7f/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/a2b0fc53611f8705640773f18c8dd6a47eed3b7f/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/a2b0fc53611f8705640773f18c8dd6a47eed3b7f/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/a2b0fc53611f8705640773f18c8dd6a47eed3b7f/attempt_1/transformed");

        // CRITICAL: Preserve formatting and comments using Sniper
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // Enable NoClasspath mode (robustness)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new FlywayProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring Complete. Check output in " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}