package org.flywaydb.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlywayRefactoring {

    /**
     * Processor to handle the Flyway 5.x -> 6.x API changes.
     * Focuses on converting 'new Flyway()' to 'Flyway.configure()'
     * and updating subsequent method calls to the Fluent API.
     */
    public static class FlywayMigrationProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        private static final String FLYWAY_CLASS = "org.flywaydb.core.Flyway";
        private static final String FLUENT_CONFIG_CLASS = "org.flywaydb.core.api.configuration.FluentConfiguration";
        
        // Methods that existed on Flyway setter API but map to FluentConfiguration methods (renamed or same)
        private static final Set<String> CONFIG_METHODS = new HashSet<>(Arrays.asList(
            "setDataSource", "dataSource",
            "setLocations", "locations",
            "setValidateOnMigrate", "validateOnMigrate",
            "setSchemas", "schemas",
            "setTable", "table",
            "setBaselineVersion", "baselineVersion",
            "setBaselineDescription", "baselineDescription"
        ));

        // Methods that trigger execution and require the Flyway object (via .load())
        private static final Set<String> EXECUTION_METHODS = new HashSet<>(Arrays.asList(
            "migrate", "clean", "info", "validate", "undo", "baseline", "repair"
        ));

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // Check for 'new Flyway()'
            CtTypeReference<?> type = candidate.getType();
            return type != null && type.getQualifiedName().equals(FLYWAY_CLASS) 
                   && candidate.getArguments().isEmpty();
        }

        @Override
        public void process(CtConstructorCall<?> ctorCall) {
            Factory factory = getFactory();

            // 1. Create replacement: Flyway.configure()
            CtTypeReference<?> flywayType = factory.Type().createReference(FLYWAY_CLASS);
            CtTypeReference<?> fluentConfigType = factory.Type().createReference(FLUENT_CONFIG_CLASS);

            CtInvocation<?> configureCall = factory.Code().createInvocation(
                factory.Code().createTypeAccess(flywayType),
                factory.Method().createReference(flywayType, fluentConfigType, "configure")
            );

            // 2. Identify context (Variable Declaration or Assignment)
            CtElement parent = ctorCall.getParent();
            CtVariable<?> variable = null;

            if (parent instanceof CtLocalVariable) {
                variable = (CtLocalVariable<?>) parent;
            } else if (parent instanceof CtAssignment) {
                CtExpression<?> assigned = ((CtAssignment<?,?>) parent).getAssigned();
                if (assigned instanceof CtVariableAccess) {
                    variable = ((CtVariableAccess<?>) assigned).getVariable().getDeclaration();
                }
            }

            // 3. Apply transformation
            if (variable != null) {
                // Change variable type to FluentConfiguration
                variable.setType((CtTypeReference) fluentConfigType);
                ctorCall.replace(configureCall);

                // Update usages of this variable
                updateVariableUsages(variable, factory);
            } else {
                // If used anonymously (new Flyway().migrate()), just chain .load()
                // replacement: Flyway.configure().load()
                CtInvocation<?> loadCall = factory.Code().createInvocation(
                    configureCall,
                    factory.Method().createReference(fluentConfigType, flywayType, "load")
                );
                ctorCall.replace(loadCall);
            }
        }

        private void updateVariableUsages(CtVariable<?> variable, Factory factory) {
            // Find all references to this variable in the code
            List<CtReferenceExpression> references = spoon.reflect.visitor.Query.getReferences(variable.getParent().getParent(), variable.getReference());

            for (CtReferenceExpression ref : references) {
                CtElement parent = ref.getParent();

                // Case A: Method Invocation on the variable (var.method())
                if (parent instanceof CtInvocation && ((CtInvocation<?>) parent).getTarget() == ref) {
                    CtInvocation<?> invocation = (CtInvocation<?>) parent;
                    String methodName = invocation.getExecutable().getSimpleName();

                    if (CONFIG_METHODS.contains(methodName)) {
                        // Rename setters: setDataSource -> dataSource
                        if (methodName.startsWith("set")) {
                            String newName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                            invocation.getExecutable().setSimpleName(newName);
                        }
                    } else if (EXECUTION_METHODS.contains(methodName)) {
                        // Execution method: var.migrate() -> var.load().migrate()
                        injectLoadCall(ref, factory);
                    } else {
                        // Unknown method. Assume it belongs to Flyway, so we need .load()
                        injectLoadCall(ref, factory);
                    }
                }
                // Case B: Variable passed as argument or returned
                else if (parent instanceof CtInvocation || parent instanceof CtReturn) {
                    // method(var) -> method(var.load())
                    injectLoadCall(ref, factory);
                }
            }
        }

        private void injectLoadCall(CtExpression<?> varRef, Factory factory) {
            // Prevent double injection if already processed
            if (varRef.getParent() instanceof CtInvocation) {
                CtInvocation<?> parent = (CtInvocation<?>) varRef.getParent();
                if ("load".equals(parent.getExecutable().getSimpleName())) {
                    return;
                }
            }

            CtTypeReference<?> fluentConfigType = factory.Type().createReference(FLUENT_CONFIG_CLASS);
            CtTypeReference<?> flywayType = factory.Type().createReference(FLYWAY_CLASS);

            // Clone the ref to avoid parenting issues during replacement
            CtExpression<?> varClone = varRef.clone();

            CtInvocation<?> loadCall = factory.Code().createInvocation(
                varClone,
                factory.Method().createReference(fluentConfigType, flywayType, "load")
            );

            varRef.replace(loadCall);
        }
    }

    /**
     * Processor to handle MigrationType -> CoreMigrationType rename.
     */
    public static class MigrationTypeProcessor extends AbstractProcessor<CtElement> {
        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            if (candidate instanceof CtTypeReference) {
                CtTypeReference<?> ref = (CtTypeReference<?>) candidate;
                return "org.flywaydb.core.api.MigrationType".equals(ref.getQualifiedName());
            }
            return false;
        }

        @Override
        public void process(CtElement candidate) {
            CtTypeReference<?> ref = (CtTypeReference<?>) candidate;
            ref.setPackage(ref.getFactory().Package().getOrCreate("org.flywaydb.core.api"));
            ref.setSimpleName("CoreMigrationType");
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/ea33b5101edffc0242967cbf21c1016378b18483/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java"; // Default input
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ea33b5101edffc0242967cbf21c1016378b18483/attempt_1/transformed"; // Default output

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ea33b5101edffc0242967cbf21c1016378b18483/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ea33b5101edffc0242967cbf21c1016378b18483/attempt_1/transformed");

        // CRITICAL: Configure Sniper Printer for preserving format/comments
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // Robustness for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        // Add processors
        launcher.addProcessor(new FlywayMigrationProcessor());
        launcher.addProcessor(new MigrationTypeProcessor());

        System.out.println("Starting Flyway Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring completed. Check output at: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}