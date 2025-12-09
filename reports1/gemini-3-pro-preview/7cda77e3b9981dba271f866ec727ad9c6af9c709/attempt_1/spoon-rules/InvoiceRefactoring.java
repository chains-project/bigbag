package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class InvoiceRefactoring {

    public static class InvoiceProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"setTotal".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // If we detect it is ALREADY a BigDecimal, we skip it.
            // If type is null (unknown) or primitive, we assume it needs processing.
            if (type != null && type.getQualifiedName().contains("BigDecimal")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // We verify the owner is likely 'Invoice'. We allow null/unknown to avoid skipping valid calls in partial classpaths.
            if (owner != null && !owner.getQualifiedName().contains("Invoice") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Transformation: Wrap originalArg inside BigDecimal.valueOf(...)
            CtTypeReference<?> bigDecimalRef = factory.Type().createReference("java.math.BigDecimal");

            // Create: BigDecimal.valueOf(originalArg)
            // Note: We use wildcards <?> to ensure generic safety
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(bigDecimalRef),
                factory.Method().createReference(
                    bigDecimalRef, 
                    bigDecimalRef, // return type
                    "valueOf", 
                    factory.Type().doublePrimitiveType() // arg type
                ),
                originalArg.clone()
            );

            // Apply replacement
            originalArg.replace(replacement);
            
            System.out.println("Refactored setTotal at " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/7cda77e3b9981dba271f866ec727ad9c6af9c709/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProperties.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7cda77e3b9981dba271f866ec727ad9c6af9c709/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/7cda77e3b9981dba271f866ec727ad9c6af9c709/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProperties.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7cda77e3b9981dba271f866ec727ad9c6af9c709/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: Sniper Configuration for Source Fidelity
        // ==========================================================
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Set NoClasspath to true to process files without full compilation dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new InvoiceProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}