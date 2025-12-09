package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Collections;

public class ClientRefactoring {

    /**
     * Processor to handle the migration:
     * FROM: client.connect("localhost", 8080);
     * TO:   client.connectTo(new ConnectionParams("localhost", 8080));
     */
    public static class ConnectMethodProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Old method name)
            if (!"connect".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 2) {
                return false;
            }

            // 3. Owner Check (Defensive loose matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null &&
                !owner.getQualifiedName().contains("LegacyClient") &&
                !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Argument Type Safety Check (Defensive)
            CtExpression<?> arg0 = candidate.getArguments().get(0);
            CtExpression<?> arg1 = candidate.getArguments().get(1);

            // Check first arg (should be String or unknown)
            if (arg0.getType() != null && !arg0.getType().getQualifiedName().equals("java.lang.String")) {
                // If we are sure it's NOT a string, skip. If null (unknown), we assume it might be valid.
                return false;
            }

            // Check second arg (should be int or unknown)
            if (arg1.getType() != null && !arg1.getType().unbox().getQualifiedName().equals("int")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Capture original arguments
            CtExpression<?> hostArg = invocation.getArguments().get(0);
            CtExpression<?> portArg = invocation.getArguments().get(1);

            // 1. Create reference to the new wrapper class
            CtTypeReference<?> paramsType = factory.Type().createReference("com.example.ConnectionParams");

            // 2. Create the constructor call: new ConnectionParams(host, port)
            // Note: We use wildcards <?> for generic safety
            CtConstructorCall<?> newParamsArgs = factory.Code().createConstructorCall(
                paramsType,
                hostArg.clone(),
                portArg.clone()
            );

            // 3. Update the invocation
            // Change method name
            CtExecutableReference<?> execRef = invocation.getExecutable();
            execRef.setSimpleName("connectTo");
            
            // Replace arguments with the single new wrapped argument
            invocation.setArguments(Collections.singletonList(newParamsArgs));

            System.out.println("Refactored 'connect' to 'connectTo' at " + invocation.getPosition().toString());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f5dbb3021aa96afc60b38d2b0b01873d3a6f16bd/attempt_1/transformed");

        // ========================================================================
        // CRITICAL: PRESERVE FORMATTING AND COMMENTS (Sniper Mode)
        // ========================================================================
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Inject SniperJavaPrettyPrinter manually.
        // This ensures the AST prints exactly as it was parsed, modifying only changes.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Mode: Assume libraries are missing
        launcher.getEnvironment().setNoClasspath(true);

        // Register Processor
        launcher.addProcessor(new ConnectMethodProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}