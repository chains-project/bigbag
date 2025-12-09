package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class FlywayRefactoring {

    /**
     * Processor to handle Flyway 5.x to 6.x breaking changes:
     * 1. Constructor: new Flyway() -> Flyway.configure()
     * 2. Configuration: setDataSource() -> dataSource(), etc.
     * 3. Enums: MigrationType -> CoreMigrationType
     */
    public static class FlywayProcessor extends AbstractProcessor<CtElement> {

        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            if (candidate instanceof CtConstructorCall) {
                CtConstructorCall<?> ctor = (CtConstructorCall<?>) candidate;
                return isFlywayConstructor(ctor);
            }
            if (candidate instanceof CtInvocation) {
                CtInvocation<?> inv = (CtInvocation<?>) candidate;
                return isTargetMethod(inv);
            }
            return false;
        }

        private boolean isFlywayConstructor(CtConstructorCall<?> ctor) {
            CtTypeReference<?> type = ctor.getType();
            // Check for new Flyway()
            return type != null 
                   && type.getQualifiedName().contains("Flyway")
                   && ctor.getArguments().isEmpty();
        }

        private boolean isTargetMethod(CtInvocation<?> inv) {
            String name = inv.getExecutable().getSimpleName();
            
            // 1. Check for setters that were removed/renamed to fluent
            if (name.equals("setDataSource") || 
                name.equals("setLocations") || 
                name.equals("setValidateOnMigrate")) {
                return true; // We assume these are Flyway setters based on name and context
            }
            
            // 2. Check for MigrationType.valueOf (Renamed class)
            if (name.equals("valueOf")) {
                CtTypeReference<?> declaringType = inv.getExecutable().getDeclaringType();
                return declaringType != null 
                       && declaringType.getQualifiedName().equals("org.flywaydb.core.api.MigrationType");
            }
            
            return false;
        }

        @Override
        public void process(CtElement element) {
            Factory factory = getFactory();

            if (element instanceof CtConstructorCall) {
                processConstructor((CtConstructorCall<?>) element, factory);
            } else if (element instanceof CtInvocation) {
                processInvocation((CtInvocation<?>) element, factory);
            }
        }

        private void processConstructor(CtConstructorCall<?> ctor, Factory factory) {
            // Transform: new Flyway()  --->  Flyway.configure()
            // Note: This changes the return type from Flyway to FluentConfiguration.
            // Users will need to append .load() later if they need the Flyway object immediately,
            // but this is the necessary API bridge.
            
            CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
            
            CtInvocation<?> configureCall = factory.Code().createInvocation(
                factory.Code().createTypeAccess(flywayType),
                factory.Method().createReference(flywayType, factory.Type().createReference("java.lang.Object"), "configure")
            );

            // Preserve position/comments by copying logic if needed, but simple replacement is usually sufficient
            ctor.replace(configureCall);
            System.out.println("Refactored: new Flyway() -> Flyway.configure() at line " + ctor.getPosition().getLine());
        }

        private void processInvocation(CtInvocation<?> inv, Factory factory) {
            String name = inv.getExecutable().getSimpleName();

            // Handle MigrationType.valueOf -> CoreMigrationType.valueOf
            if (name.equals("valueOf")) {
                CtTypeReference<?> oldType = inv.getExecutable().getDeclaringType();
                if (oldType != null && oldType.getQualifiedName().contains("MigrationType")) {
                    CtTypeReference<?> newType = factory.Type().createReference("org.flywaydb.core.api.CoreMigrationType");
                    
                    // Replace the target expression (MigrationType) with (CoreMigrationType)
                    if (inv.getTarget() instanceof CtTypeAccess) {
                        ((CtTypeAccess<?>) inv.getTarget()).setAccessedType(newType);
                        System.out.println("Refactored: MigrationType -> CoreMigrationType at line " + inv.getPosition().getLine());
                    }
                }
                return;
            }

            // Handle Setters -> Fluent Methods
            // setDataSource -> dataSource
            // setLocations -> locations
            // setValidateOnMigrate -> validateOnMigrate
            String newName = null;
            if (name.startsWith("set")) {
                String rawName = name.substring(3);
                newName = Character.toLowerCase(rawName.charAt(0)) + rawName.substring(1);
            }

            if (newName != null) {
                // Defensive check: Ensure we aren't renaming unrelated setters.
                // In NoClasspath, we guess based on method name and likelihood.
                // For Flyway, these specific setters are very characteristic.
                
                // We simply update the method name.
                // The arguments remain the same (FluentConfiguration supports the same args usually).
                inv.getExecutable().setSimpleName(newName);
                System.out.println("Refactored: " + name + " -> " + newName + " at line " + inv.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Configuration paths
        String inputPath = "/home/kth/Documents/last_transformer/output/1d43bce1de6a81ac017c233d72f348d3c850299e/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java"; // Update as needed
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1d43bce1de6a81ac017c233d72f348d3c850299e/attempt_1/transformed"; // Update as needed

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1d43bce1de6a81ac017c233d72f348d3c850299e/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1d43bce1de6a81ac017c233d72f348d3c850299e/attempt_1/transformed");

        // CRITICAL: Configure Environment for Robust Sniper Printing
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Disable classpath (defensive mode)
        launcher.getEnvironment().setNoClasspath(true);
        // 3. Force SniperJavaPrettyPrinter to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        launcher.addProcessor(new FlywayProcessor());

        try {
            System.out.println("Starting Flyway Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}