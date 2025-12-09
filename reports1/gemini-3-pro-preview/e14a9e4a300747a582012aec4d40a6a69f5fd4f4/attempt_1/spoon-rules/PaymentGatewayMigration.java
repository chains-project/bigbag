package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class PaymentGatewayMigration {

    /**
     * Processor to handle the migration of Gateway.charge(double) to Gateway.charge(BigDecimal).
     */
    public static class ChargeMethodProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"charge".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Logic:
            // If type is explicitly BigDecimal, it's already migrated -> Skip.
            // If type is null (unknown) or primitive (double/float/int) -> Process it.
            if (type != null && type.getQualifiedName().contains("BigDecimal")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath safety)
            // We want to avoid modifying 'charge' methods on unrelated classes.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && 
                !owner.getQualifiedName().contains("Gateway") && 
                !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Create reference to java.math.BigDecimal
            CtTypeReference<?> bigDecimalRef = factory.Type().createReference("java.math.BigDecimal");

            // Transformation: Wrap originalArg inside BigDecimal.valueOf(...)
            // We use valueOf() because it handles doubles better than the constructor
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(bigDecimalRef),
                factory.Method().createReference(
                    bigDecimalRef, 
                    factory.Type().voidPrimitiveType(), 
                    "valueOf", 
                    factory.Type().doublePrimitiveType()
                ),
                originalArg.clone()
            );

            // Replace the argument in the original invocation
            originalArg.replace(replacement);
            
            System.out.println("Refactored 'charge' invocation at " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().toString() : "unknown location"));
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/http/IdsHttpService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/e14a9e4a300747a582012aec4d40a6a69f5fd4f4/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION FOR SOURCE PRESERVATION ---
        // 1. Enable comment recording
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force SniperJavaPrettyPrinter to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new ChargeMethodProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}