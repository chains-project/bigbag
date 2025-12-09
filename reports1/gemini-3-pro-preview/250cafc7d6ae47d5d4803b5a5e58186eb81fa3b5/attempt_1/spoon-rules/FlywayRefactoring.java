package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtLocalVariable;
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
     * Processor to handle Flyway instantiation and configuration migration.
     * Transformation:
     * 1. Flyway f = new Flyway() -> FluentConfiguration f = Flyway.configure()
     * 2. f.setDataSource(...) -> f.dataSource(...)
     * 3. f.migrate() -> f.load().migrate()
     */
    public static class FlywayInitProcessor extends AbstractProcessor<CtLocalVariable<?>> {

        private static final Set<String> EXECUTION_METHODS = new HashSet<>(Arrays.asList(
                "migrate", "clean", "info", "validate", "baseline", "repair"
        ));

        @Override
        public boolean isToBeProcessed(CtLocalVariable<?> candidate) {
            // Check type is Flyway
            CtTypeReference<?> type = candidate.getType();
            if (type == null || !type.getQualifiedName().contains("Flyway")) {
                return false;
            }

            // Check init is 'new Flyway()'
            CtExpression<?> defaultExp = candidate.getDefaultExpression();
            if (!(defaultExp instanceof CtConstructorCall)) {
                return false;
            }

            CtConstructorCall<?> ctorCall = (CtConstructorCall<?>) defaultExp;
            return ctorCall.getArguments().isEmpty();
        }

        @Override
        public void process(CtLocalVariable<?> variable) {
            Factory factory = getFactory();
            String varName = variable.getSimpleName();

            // 1. Change Variable Type: Flyway -> FluentConfiguration
            CtTypeReference<?> fluentConfigRef = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            variable.setType((CtTypeReference) fluentConfigRef);

            // 2. Change Init: new Flyway() -> Flyway.configure()
            CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
            CtInvocation<?> configureCall = factory.Code().createInvocation(
                    factory.Code().createTypeAccess(flywayType),
                    factory.Method().createReference(flywayType, fluentConfigRef, "configure")
            );
            variable.setDefaultExpression(configureCall);

            // 3. Find and update all usages in the scope
            // Note: In NoClasspath, we scan by variable name within the parent block
            CtElement scope = variable.getParent();
            List<CtVariableAccess> usages = scope.getElements(new TypeFilter<CtVariableAccess>(CtVariableAccess.class) {
                @Override
                public boolean matches(CtVariableAccess element) {
                    return varName.equals(element.getVariable().getSimpleName()) && super.matches(element);
                }
            });

            for (CtVariableAccess usage : usages) {
                if (usage.getParent() instanceof CtInvocation) {
                    CtInvocation<?> invocation = (CtInvocation<?>) usage.getParent();
                    
                    // Ensure the usage is the Target of the invocation (e.g. usage.method())
                    if (invocation.getTarget() != usage) {
                        continue;
                    }

                    String methodName = invocation.getExecutable().getSimpleName();

                    if (EXECUTION_METHODS.contains(methodName)) {
                        // CASE: Execution method (migrate, clean, etc.)
                        // Transformation: var.method() -> var.load().method()
                        
                        // Create '.load()' invocation
                        CtInvocation<?> loadCall = factory.Code().createInvocation(
                                usage.clone(), // var
                                factory.Method().createReference(fluentConfigRef, flywayType, "load")
                        );
                        
                        // Replace target of original invocation
                        invocation.setTarget(loadCall);
                        
                    } else if (methodName.startsWith("set") && methodName.length() > 3) {
                        // CASE: Configuration Setter (setDataSource -> dataSource)
                        // Transformation: Rename method, lowercase first char of property
                        String newName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                        invocation.getExecutable().setSimpleName(newName);
                    }
                }
            }
            
            System.out.println("Refactored Flyway usage for variable: " + varName);
        }
    }

    /**
     * Processor to handle MigrationType enum removal.
     * Transformation: MigrationType.valueOf(...) -> CoreMigrationType.valueOf(...)
     */
    public static class MigrationTypeProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            if (!"valueOf".equals(candidate.getExecutable().getSimpleName())) return false;
            
            CtExpression<?> target = candidate.getTarget();
            if (target == null) return false;
            
            String targetName = target.toString();
            // Check for simple name or qualified name match
            return targetName.equals("MigrationType") || targetName.endsWith(".MigrationType");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Create reference to new CoreMigrationType
            CtTypeReference<?> coreMigrationRef = factory.Type().createReference("org.flywaydb.core.api.CoreMigrationType");
            
            // Create new type access
            CtTypeAccess<?> newTarget = factory.Code().createTypeAccess(coreMigrationRef);
            
            // Replace the target
            invocation.setTarget(newTarget);
            
            // Update executable declaring type to match (helps mostly for metadata)
            invocation.getExecutable().setDeclaringType((CtTypeReference) coreMigrationRef);
            
            System.out.println("Refactored MigrationType.valueOf at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/250cafc7d6ae47d5d4803b5a5e58186eb81fa3b5/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java"; // Default input
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/250cafc7d6ae47d5d4803b5a5e58186eb81fa3b5/attempt_1/transformed"; // Default output

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/250cafc7d6ae47d5d4803b5a5e58186eb81fa3b5/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/250cafc7d6ae47d5d4803b5a5e58186eb81fa3b5/attempt_1/transformed");

        // CRITICAL: Configure Sniper Printer for preservation
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
                () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        launcher.getEnvironment().setNoClasspath(true);

        // Add Processors
        launcher.addProcessor(new FlywayInitProcessor());
        launcher.addProcessor(new MigrationTypeProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}