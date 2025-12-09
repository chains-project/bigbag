package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class LogbackRefactoring {

    /**
     * Processor to migrate setLevel(Level.X) calls to setLevel("X").
     * Based on the diff: ch.qos.logback.classic.model.LoggerModel.setLevel(String) [NEW].
     */
    public static class LevelToStringProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"setLevel".equals(methodName)) {
                return false;
            }

            // 2. Check Argument Count
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Defensive Type Check (NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();

            // If the argument is already a String, skip it
            if (argType != null && "java.lang.String".equals(argType.getQualifiedName())) {
                return false;
            }

            // We specifically look for Field Accesses (e.g., Level.INFO)
            if (!(arg instanceof CtFieldAccess)) {
                return false;
            }

            // 4. Verify the argument is a 'Level' constant
            // Use relaxed string matching for NoClasspath robustness
            CtFieldAccess<?> fieldAccess = (CtFieldAccess<?>) arg;
            CtTypeReference<?> declaringType = fieldAccess.getVariable().getDeclaringType();

            if (declaringType == null) {
                return false; // Cannot determine owner
            }

            String typeName = declaringType.getQualifiedName();
            boolean isLogbackLevel = typeName.contains("ch.qos.logback.classic.Level");
            
            // Allow unqualified "Level" if exact package isn't resolved
            boolean isGenericLevel = typeName.endsWith("Level") && !typeName.startsWith("java.util.logging");

            return isLogbackLevel || isGenericLevel;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // We know it is a FieldAccess from isToBeProcessed
            CtFieldAccess<?> fieldAccess = (CtFieldAccess<?>) originalArg;
            
            // Extract the level name (e.g., "INFO", "DEBUG")
            String levelName = fieldAccess.getVariable().getSimpleName();

            // Transformation: Create "INFO" string literal
            CtLiteral<String> stringArgument = factory.Code().createLiteral(levelName);

            // Replace the argument
            originalArg.replace(stringArgument);
            
            System.out.println("Refactored setLevel(" + levelName + ") to String at line " 
                + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/1820a966ae02ad8df44d0a0106cba65ceaf3aa95/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1820a966ae02ad8df44d0a0106cba65ceaf3aa95/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1820a966ae02ad8df44d0a0106cba65ceaf3aa95/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1820a966ae02ad8df44d0a0106cba65ceaf3aa95/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 10/11+
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Robustness for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);

        launcher.addProcessor(new LevelToStringProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}