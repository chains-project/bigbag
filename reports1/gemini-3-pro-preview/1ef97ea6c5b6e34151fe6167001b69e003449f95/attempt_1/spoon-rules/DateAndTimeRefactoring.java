package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class DateAndTimeRefactoring {

    public static class DateAndTimeProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"between".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (2 or 3 arguments supported in both old and new versions)
            int argCount = candidate.getArguments().size();
            if (argCount != 2 && argCount != 3) {
                return false;
            }

            // 3. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("DateAndTime") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Argument Type Check (Defensive)
            // We check if the first argument is already a Timestamp. If so, we assume migration is done.
            CtExpression<?> firstArg = candidate.getArguments().get(0);
            CtTypeReference<?> type = firstArg.getType();

            if (type != null && type.getQualifiedName().contains("Timestamp")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // We need to upgrade arguments at index 0 and index 1 from Date to Timestamp
            // Method signature changed from:
            // between(Date, Date, ...) -> between(Timestamp, Timestamp, ...)
            
            wrapArgumentInTimestamp(invocation.getArguments().get(0));
            wrapArgumentInTimestamp(invocation.getArguments().get(1));
            
            System.out.println("Refactored DateAndTime.between(...) at line " + invocation.getPosition().getLine());
        }

        private void wrapArgumentInTimestamp(CtExpression<?> arg) {
            // Skip null literals (null is compatible with Timestamp)
            if (arg instanceof CtLiteral && ((CtLiteral<?>) arg).getValue() == null) {
                return;
            }

            // Defensive: Check if already Timestamp (double check to avoid double wrapping during partial runs)
            if (arg.getType() != null && arg.getType().getQualifiedName().contains("Timestamp")) {
                return;
            }

            Factory factory = getFactory();

            // 1. Create Reference to java.sql.Timestamp
            CtTypeReference<Object> timestampType = factory.Type().createReference("java.sql.Timestamp");

            // 2. Create Reference to .getTime() method
            // We assume the underlying object has a .getTime() method (like java.util.Date)
            // In NoClasspath, we construct the reference manually to be safe.
            CtExecutableReference<Long> getTimeRef = factory.Core().createExecutableReference();
            getTimeRef.setSimpleName("getTime");
            getTimeRef.setType(factory.Type().longPrimitiveType());
            getTimeRef.setDeclaringType(factory.Type().createReference("java.util.Date"));

            // 3. Create invocation: arg.getTime()
            CtInvocation<?> getTimeCall = factory.Code().createInvocation(
                arg.clone(),
                getTimeRef
            );

            // 4. Create constructor call: new java.sql.Timestamp(arg.getTime())
            CtConstructorCall<?> newTimestamp = factory.Code().createConstructorCall(
                timestampType,
                getTimeCall
            );

            // 5. Replace the original argument
            arg.replace(newTimestamp);
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/1ef97ea6c5b6e34151fe6167001b69e003449f95/flink-faker/src/main/java/com/github/knaufk/flink/faker/DateTime.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1ef97ea6c5b6e34151fe6167001b69e003449f95/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1ef97ea6c5b6e34151fe6167001b69e003449f95/flink-faker/src/main/java/com/github/knaufk/flink/faker/DateTime.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1ef97ea6c5b6e34151fe6167001b69e003449f95/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/comments
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new DateAndTimeProcessor());
        
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}