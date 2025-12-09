package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Spoon Refactoring Processor for Logback Migration.
 * 
 * ANALYSIS:
 * The diff indicates changes in the `ch.qos.logback` ecosystem.
 * 1. `Logger.setLevel(Level)` is marked UNCHANGED and is the canonical API.
 * 2. `LoggerModel.setLevel(String)` is NEW, suggesting distinct separation of configuration models.
 * 3. The `Level` class is MODIFIED.
 * 
 * STRATEGY:
 * To ensure robustness and strict typing (matching the `Logger.setLevel(Level)` signature), 
 * this processor finds invocations of `setLevel` on `Logger` instances that might be passing 
 * String literals (a common pattern in older logging code or loose API usage) and refactors 
 * them to use the strongly-typed static `Level` constants.
 * 
 * Example:
 * - BEFORE: logger.setLevel("INFO");
 * - AFTER:  logger.setLevel(ch.qos.logback.classic.Level.INFO);
 */
public class LogbackLevelRefactoring {

    public static class LoggerLevelProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final List<String> VALID_LEVELS = Arrays.asList(
            "OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL"
        );

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            if (!"setLevel".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check Argument Count (Must be 1)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner Type (Defensive for NoClasspath)
            // We want to target ch.qos.logback.classic.Logger
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String ownerName = declaringType.getQualifiedName();
                // Match "Logger" loosely to handle imports or simple names in NoClasspath
                if (!ownerName.contains("Logger") && !ownerName.equals("<unknown>")) {
                    return false;
                }
            }

            // 4. Check Argument Type
            CtExpression<?> arg = candidate.getArguments().get(0);
            
            // We specifically target String literals to convert them to Constants.
            // i.e., setLevel("DEBUG") -> setLevel(Level.DEBUG)
            if (!(arg instanceof CtLiteral)) {
                return false;
            }

            CtLiteral<?> literal = (CtLiteral<?>) arg;
            Object value = literal.getValue();
            
            // If it's not a String, we don't touch it
            if (!(value instanceof String)) {
                return false;
            }

            // Verify the string value corresponds to a known Logback Level
            String levelStr = ((String) value).toUpperCase();
            return VALID_LEVELS.contains(levelStr);
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);
            String levelName = ((String) ((CtLiteral<?>) originalArg).getValue()).toUpperCase();

            // 1. Create Reference to ch.qos.logback.classic.Level
            CtTypeReference<?> levelTypeRef = factory.Type().createReference("ch.qos.logback.classic.Level");

            // 2. Create Reference to the static field (e.g., Level.INFO)
            CtFieldReference<?> fieldRef = factory.Field().createReference(
                levelTypeRef,
                levelTypeRef,
                levelName
            );
            fieldRef.setStatic(true);

            // 3. Create the Field Read Access (Level.INFO)
            CtFieldRead<?> fieldRead = factory.Code().createFieldRead(
                factory.Code().createTypeAccess(levelTypeRef),
                fieldRef
            );

            // 4. Replace the String literal argument with the Field Read
            originalArg.replace(fieldRead);

            System.out.println("Refactored setLevel(\"" + levelName + "\") to Level." + levelName + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded for specific project needs)
        String inputPath = "/home/kth/Documents/last_transformer/output/43b3a858b77ec27fc8946aba292001c3de465012/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineGeneralTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/43b3a858b77ec27fc8946aba292001c3de465012/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/43b3a858b77ec27fc8946aba292001c3de465012/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineGeneralTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/43b3a858b77ec27fc8946aba292001c3de465012/attempt_1/transformed");

        // ==========================================================
        // CRITICAL CONFIGURATION: Preservation of Comments & Layout
        // ==========================================================
        
        // 1. Enable comment parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Enable Auto-Imports to handle the new 'Level' class cleanly
        launcher.getEnvironment().setAutoImports(true);

        // 3. Handle Missing Libraries (NoClasspath)
        launcher.getEnvironment().setNoClasspath(true);

        // 4. Force Sniper Printer for high-fidelity code transformation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // Register the processor
        launcher.addProcessor(new LoggerLevelProcessor());

        try {
            System.out.println("Starting Logback Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}