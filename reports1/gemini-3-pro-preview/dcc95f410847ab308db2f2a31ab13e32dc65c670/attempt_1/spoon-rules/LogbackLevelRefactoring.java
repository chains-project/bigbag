package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class LogbackLevelRefactoring {

    /**
     * Processor to migrate setLevel(Level.X) to setLevel("X").
     */
    public static class LevelArgumentProcessor extends AbstractProcessor<CtInvocation<?>> {

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

            // 3. Check Argument Type (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);

            // We are looking for Field Reads (e.g., Level.INFO or static import INFO)
            if (!(arg instanceof CtFieldRead)) {
                return false;
            }

            CtFieldRead<?> fieldRead = (CtFieldRead<?>) arg;
            CtFieldReference<?> variable = fieldRead.getVariable();
            
            // Defensively check the declaring type of the field
            CtTypeReference<?> declaringType = variable.getDeclaringType();

            // Match 'ch.qos.logback.classic.Level' loosely to handle incomplete classpaths
            if (declaringType != null && declaringType.getQualifiedName().contains("ch.qos.logback.classic.Level")) {
                return true;
            }
            
            // Fallback: if resolving failed, check if the variable name is a known Log Level
            // and the target looks like "Level" (simple heuristic)
            if (declaringType == null && arg.toString().startsWith("Level.")) {
               String fieldName = variable.getSimpleName();
               return isLogLevelName(fieldName);
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            CtFieldRead<?> fieldRead = (CtFieldRead<?>) invocation.getArguments().get(0);
            String levelName = fieldRead.getVariable().getSimpleName();

            // Double check validation to avoid transforming unrelated constants
            if (isLogLevelName(levelName)) {
                // Transformation: Create String literal "INFO"
                CtLiteral<String> replacement = getFactory().Code().createLiteral(levelName);
                
                // Replace the argument
                fieldRead.replace(replacement);
                
                System.out.println("Refactored setLevel(" + levelName + ") to setLevel(\"" + levelName + "\") at line " + invocation.getPosition().getLine());
            }
        }

        private boolean isLogLevelName(String name) {
            return "OFF".equals(name) || "ERROR".equals(name) || "WARN".equals(name) || 
                   "INFO".equals(name) || "DEBUG".equals(name) || "TRACE".equals(name) || "ALL".equals(name);
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/dcc95f410847ab308db2f2a31ab13e32dc65c670/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/dcc95f410847ab308db2f2a31ab13e32dc65c670/attempt_1/transformed";

        // Allow arguments to override paths
        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/dcc95f410847ab308db2f2a31ab13e32dc65c670/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/dcc95f410847ab308db2f2a31ab13e32dc65c670/attempt_1/transformed");

        // =========================================================
        // CRITICAL: Preserve formatting using SniperJavaPrettyPrinter
        // =========================================================
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // Defensive: Assume library JARs might be missing
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new LevelArgumentProcessor());

        try {
            System.out.println("Starting Logback Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}