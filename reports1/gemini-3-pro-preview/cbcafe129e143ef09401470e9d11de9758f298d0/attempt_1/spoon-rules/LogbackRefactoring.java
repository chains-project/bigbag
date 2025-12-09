package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class LogbackRefactoring {

    /**
     * Processor to migrate LoggerModel.setLevel(Level) calls to LoggerModel.setLevel(String).
     * NOTE: This explicitly excludes 'Logger' which retains the setLevel(Level) signature.
     */
    public static class SetLevelProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check method name
            if (!"setLevel".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check argument count (must be 1)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner Type (Defensive NoClasspath)
            // We only want to refactor LoggerModel and RootLoggerModel, NOT the main Logger class.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null) {
                String ownerName = owner.getQualifiedName();
                // If it's the standard Logger, SKIP it (Unchanged in diff)
                if (ownerName.equals("ch.qos.logback.classic.Logger")) {
                    return false;
                }
                // If it's not a LoggerModel, SKIP it
                if (!ownerName.contains("LoggerModel") && !ownerName.equals("<unknown>")) {
                    return false;
                }
            }

            // 4. Check Argument Type
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();

            // We are looking for arguments of type 'Level'
            if (argType != null) {
                // If it's already a String, skip
                if (argType.getQualifiedName().equals("java.lang.String")) {
                    return false;
                }
                // If it's not Level (and not unknown), skip
                if (!argType.getQualifiedName().contains("Level")) {
                    return false;
                }
            } else {
                // Fallback for NoClasspath: Check if the source code implies Level usage
                if (!arg.toString().contains("Level") && !arg.toString().matches("[A-Z_]+")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);
            CtExpression<?> replacementArg;

            // Strategy: Convert Level to String
            // Optimization: If passing Level.INFO, replace with "INFO" directly
            if (originalArg instanceof CtFieldRead) {
                CtFieldRead<?> fieldRead = (CtFieldRead<?>) originalArg;
                String fieldName = fieldRead.getVariable().getSimpleName();
                // Assuming standard Logback levels (INFO, DEBUG, ERROR, etc.)
                if (isStandardLevel(fieldName)) {
                    replacementArg = factory.Code().createLiteral(fieldName);
                } else {
                    // Unknown constant, use toString()
                    replacementArg = createToStringInvocation(factory, originalArg);
                }
            } 
            // Handle static access like Level.INFO directly if parsed differently
            else if (originalArg.toString().matches(".*Level\\.[A-Z]+")) {
                 String[] parts = originalArg.toString().split("\\.");
                 String levelName = parts[parts.length - 1];
                 replacementArg = factory.Code().createLiteral(levelName);
            }
            else {
                // General case: variable.toString()
                replacementArg = createToStringInvocation(factory, originalArg);
            }

            // Apply replacement
            originalArg.replace(replacementArg);
            System.out.println("Refactored setLevel(" + originalArg + ") to String at line " + invocation.getPosition().getLine());
        }

        private boolean isStandardLevel(String name) {
            return name.equals("OFF") || name.equals("ERROR") || name.equals("WARN") || 
                   name.equals("INFO") || name.equals("DEBUG") || name.equals("TRACE") || name.equals("ALL");
        }

        private CtInvocation<?> createToStringInvocation(Factory factory, CtExpression<?> target) {
            return factory.Code().createInvocation(
                target.clone(),
                factory.Method().createReference(
                    target.getType(), 
                    factory.Type().stringType(), 
                    "toString"
                )
            );
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/cbcafe129e143ef09401470e9d11de9758f298d0/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java"; // Adjust as needed
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/cbcafe129e143ef09401470e9d11de9758f298d0/attempt_1/transformed"; // Adjust as needed

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/cbcafe129e143ef09401470e9d11de9758f298d0/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/cbcafe129e143ef09401470e9d11de9758f298d0/attempt_1/transformed");

        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for high-fidelity preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Robustness for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new SetLevelProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}