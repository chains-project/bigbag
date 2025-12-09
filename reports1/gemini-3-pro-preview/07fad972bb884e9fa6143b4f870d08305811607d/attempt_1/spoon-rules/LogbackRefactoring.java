package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LogbackRefactoring {

    /**
     * Processor to migrate setLevel(Level) calls to setLevel(String)
     * specifically for LoggerModel and RootLoggerModel.
     */
    public static class LoggerModelLevelProcessor extends AbstractProcessor<CtInvocation<?>> {

        private static final Set<String> TARGET_CLASSES = new HashSet<>(Arrays.asList(
            "ch.qos.logback.classic.model.LoggerModel",
            "ch.qos.logback.classic.model.RootLoggerModel"
        ));

        private static final String LEVEL_CLASS = "ch.qos.logback.classic.Level";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"setLevel".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Expect 1 arg)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner/Target Check
            // We must ensure we are modifying LoggerModel, NOT Logger (which still takes Level)
            CtExpression<?> target = candidate.getTarget();
            if (target == null) return false;

            CtTypeReference<?> targetType = target.getType();
            if (targetType == null) {
                // Defensive: In NoClasspath, if we can't resolve the type, we skip to avoid
                // breaking 'Logger.setLevel' which looks identical.
                return false;
            }
            
            String targetName = targetType.getQualifiedName();
            boolean isTargetModel = TARGET_CLASSES.stream().anyMatch(targetName::contains);
            
            if (!isTargetModel) {
                return false;
            }

            // 4. Argument Type Check
            // We are looking for usage of 'Level.INFO', 'Level.DEBUG', etc.
            CtExpression<?> arg = candidate.getArguments().get(0);
            
            // Check if it's a field read (e.g., Level.INFO)
            if (!(arg instanceof CtFieldRead)) {
                return false;
            }
            
            CtFieldRead<?> fieldRead = (CtFieldRead<?>) arg;
            CtTypeReference<?> declaringType = fieldRead.getVariable().getDeclaringType();
            
            // Verify the field belongs to ch.qos.logback.classic.Level
            if (declaringType == null || !declaringType.getQualifiedName().contains("Level")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> arg = invocation.getArguments().get(0);

            // We verified in isToBeProcessed that this is a FieldRead on Level (e.g. Level.INFO)
            CtFieldRead<?> levelAccess = (CtFieldRead<?>) arg;
            String levelName = levelAccess.getVariable().getSimpleName(); // "INFO", "DEBUG", etc.

            // Transformation: Replace Level.INFO with "INFO"
            CtLiteral<String> stringLiteral = factory.Code().createLiteral(levelName);
            
            // Replace the argument
            arg.replace(stringLiteral);
            
            System.out.println("Refactored LoggerModel.setLevel at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "?") + 
                ": Level." + levelName + " -> \"" + levelName + "\"");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/07fad972bb884e9fa6143b4f870d08305811607d/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/07fad972bb884e9fa6143b4f870d08305811607d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/07fad972bb884e9fa6143b4f870d08305811607d/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/07fad972bb884e9fa6143b4f870d08305811607d/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and robust refactoring
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);
        // 4. Ignore duplicate declarations in strict mode
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);

        launcher.addProcessor(new LoggerModelLevelProcessor());

        System.out.println("Starting Logback Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}