package org.flywaydb.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlywayRefactoring {

    public static class FlywayConfigurationProcessor extends AbstractProcessor<CtLocalVariable<?>> {

        private static final Set<String> EXECUTION_METHODS = new HashSet<>(Arrays.asList(
            "migrate", "clean", "info", "validate", "baseline", "repair", "undo"
        ));

        @Override
        public boolean isToBeProcessed(CtLocalVariable<?> candidate) {
            // 1. Check Type: Must be Flyway
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null || !typeRef.getQualifiedName().endsWith("Flyway")) {
                return false;
            }

            // 2. Check Initialization: Must be "new Flyway()" (no-args)
            CtExpression<?> defaultExpr = candidate.getDefaultExpression();
            if (!(defaultExpr instanceof CtConstructorCall)) {
                return false;
            }

            CtConstructorCall<?> ctorCall = (CtConstructorCall<?>) defaultExpr;
            return ctorCall.getArguments().isEmpty();
        }

        @Override
        public void process(CtLocalVariable<?> variable) {
            Factory factory = getFactory();

            // 1. Prepare Types
            CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
            CtTypeReference<?> classicConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.ClassicConfiguration");

            // 2. Change Variable Type: Flyway -> ClassicConfiguration
            variable.setType((CtTypeReference) classicConfigType);

            // 3. Change Constructor Call: new Flyway() -> new ClassicConfiguration()
            if (variable.getDefaultExpression() instanceof CtConstructorCall) {
                CtConstructorCall<?> ctorCall = (CtConstructorCall<?>) variable.getDefaultExpression();
                ctorCall.setType((CtTypeReference) classicConfigType);
                ctorCall.getExecutable().setDeclaringType((CtTypeReference) classicConfigType);
            }

            // 4. Update Usages
            // We find all references to this specific local variable
            List<CtVariableAccess<?>> usages = Query.getReferences(variable.getParent(), variable);

            for (CtVariableAccess<?> usage : usages) {
                CtElement parent = usage.getParent();

                // We only care if the variable is used as the TARGET of a method invocation
                // e.g. flyway.setDataSource(...) or flyway.migrate()
                if (parent instanceof CtInvocation) {
                    CtInvocation<?> invocation = (CtInvocation<?>) parent;
                    
                    // Ensure the usage is actually the target (not an argument)
                    if (invocation.getTarget() != usage) {
                        continue;
                    }

                    String methodName = invocation.getExecutable().getSimpleName();

                    // Strategy:
                    // If it's a configuration setter (setDataSource, setClassLoader), 
                    // it is valid on ClassicConfiguration (per diff). We leave it (or arguments might need fix, but usually types match).
                    
                    // If it's an execution method (migrate, clean), it is NOT on ClassicConfiguration.
                    // We must wrap: new Flyway(config).migrate()
                    if (EXECUTION_METHODS.contains(methodName)) {
                        refactorExecutionMethod(factory, invocation, flywayType, usage);
                    }
                    
                    // Special Case: setLocations(String[]) -> might need handling if ClassicConfig only takes Location[]
                    // For now, we assume ClassicConfiguration might have string overloads or let the user fix specific type mismatches.
                    // The structural change is the priority.
                }
            }
            
            System.out.println("Refactored Flyway instantiation at line " + variable.getPosition().getLine());
        }

        private void refactorExecutionMethod(Factory factory, CtInvocation<?> invocation, CtTypeReference<?> flywayRef, CtExpression<?> configVarAccess) {
            // Target: flyway.migrate() 
            // Goal: new Flyway(flyway).migrate()

            // 1. Create 'new Flyway(config)'
            CtConstructorCall<?> newFlywayCall = factory.Code().createConstructorCall(
                flywayRef,
                configVarAccess.clone() // Pass the variable (now ClassicConfig) as arg
            );

            // 2. Replace the target of the invocation
            // invocation.setTarget(newFlywayCall) would work, but sometimes safer to rebuild
            invocation.setTarget(newFlywayCall);
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/ad80bdff62b1b0520d3fb9e8d627532a38a7c60c/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java"; // Default input
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ad80bdff62b1b0520d3fb9e8d627532a38a7c60c/attempt_1/transformed"; // Default output

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ad80bdff62b1b0520d3fb9e8d627532a38a7c60c/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ad80bdff62b1b0520d3fb9e8d627532a38a7c60c/attempt_1/transformed");

        // CRITICAL: Preserve formatting and comments
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // Defensive: NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new FlywayConfigurationProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Helper to find references (simplified version of Query.getReferences logic for local vars in scope)
    static class Query {
        static List<CtVariableAccess<?>> getReferences(CtElement scope, CtLocalVariable<?> var) {
            return scope.getElements(new TypeFilter<CtVariableAccess<?>>(CtVariableAccess.class) {
                @Override
                public boolean matches(CtVariableAccess<?> element) {
                    return super.matches(element) && 
                           element.getVariable().getSimpleName().equals(var.getSimpleName());
                }
            });
        }
    }
}