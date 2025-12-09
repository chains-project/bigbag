package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Transformation Rule
 * Generated based on inferred diff (Input was empty, assuming int -> Duration pattern):
 * - METHOD com.app.Configuration.setTimeout(int) [REMOVED]
 * + METHOD com.app.Configuration.setTimeout(java.time.Duration) [ADDED]
 */
public class ConfigurationRefactoring {

    public static class TimeoutProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"setTimeout".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Strategy:
            // If type is explicitly Duration (already migrated), skip.
            // If type is null (unknown due to NoClasspath) or primitive (int), process it.
            if (type != null && type.getQualifiedName().contains("Duration")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for safety)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null 
                && !owner.getQualifiedName().contains("Configuration") 
                && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Logic: Wrap the original 'int' argument in Duration.ofMillis(...)
            // 1. Create reference to java.time.Duration
            CtTypeReference<?> durationRef = factory.Type().createReference("java.time.Duration");

            // 2. Create invocation: Duration.ofMillis(originalArg)
            CtInvocation<?> replacement = factory.Code().createInvocation(
                factory.Code().createTypeAccess(durationRef),
                factory.Method().createReference(
                    durationRef, 
                    factory.Type().voidPrimitiveType(), 
                    "ofMillis", 
                    factory.Type().integerPrimitiveType()
                ),
                originalArg.clone() // Clone essential to detach from old parent
            );

            // 3. Apply replacement
            originalArg.replace(replacement);
            
            System.out.println("Refactored setTimeout at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths - adjust as necessary
        String inputPath = "/home/kth/Documents/last_transformer/output/9dbbd30590fa87f3e719615e0816c231106e01de/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/DapsValidator.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9dbbd30590fa87f3e719615e0816c231106e01de/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9dbbd30590fa87f3e719615e0816c231106e01de/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/DapsValidator.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9dbbd30590fa87f3e719615e0816c231106e01de/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION START ---
        
        // 1. Enable comments to prevent loss during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter to preserve formatting/indentation of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);
        
        // --- CRITICAL CONFIGURATION END ---

        launcher.addProcessor(new TimeoutProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}