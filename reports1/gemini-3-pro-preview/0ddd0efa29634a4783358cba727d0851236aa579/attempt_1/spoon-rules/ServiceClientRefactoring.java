package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.ArrayList;
import java.util.List;

public class ServiceClientRefactoring {

    /**
     * Processor to handle the migration of ServiceClient.init(String, int, int)
     * to ServiceClient.init(new ServiceConfig(String, int, int)).
     */
    public static class InitMethodProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"init".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (Old signature had 3 args)
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 3) {
                return false;
            }

            // 3. Owner/Scope Check (Defensive text matching for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null && !declaringType.getQualifiedName().contains("ServiceClient")) {
                // If the type is known and NOT ServiceClient, skip.
                // If type is unknown (null or <unknown>), we continue cautiously.
                return false;
            }

            // 4. Argument Type Safety Check (Defensive)
            // We check if the first argument looks like a String.
            CtExpression<?> firstArg = args.get(0);
            CtTypeReference<?> type = firstArg.getType();
            
            // If we have type info, ensure it is NOT already the new config object
            if (type != null && type.getQualifiedName().contains("ServiceConfig")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // 1. Capture original arguments
            List<CtExpression<?>> originalArgs = new ArrayList<>(invocation.getArguments());
            
            // 2. Create reference to the new Configuration class
            CtTypeReference<?> configTypeRef = factory.Type().createReference("com.legacy.ServiceConfig");

            // 3. Create the `new ServiceConfig(arg1, arg2, arg3)` expression
            // Note: We clone arguments to detach them from their current parent
            CtConstructorCall<?> newConfigObject = factory.Code().createConstructorCall(
                configTypeRef,
                originalArgs.get(0).clone(),
                originalArgs.get(1).clone(),
                originalArgs.get(2).clone()
            );

            // 4. Clear old arguments from the invocation and add the new wrapped argument
            // We modify the argument list directly to ensure proper AST updates
            invocation.setArguments(List.of(newConfigObject));

            System.out.println("Refactored ServiceClient.init at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default Configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/0ddd0efa29634a4783358cba727d0851236aa579/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProducer.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0ddd0efa29634a4783358cba727d0851236aa579/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0ddd0efa29634a4783358cba727d0851236aa579/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ConfigProducer.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0ddd0efa29634a4783358cba727d0851236aa579/attempt_1/transformed");

        // =========================================================
        // CRITICAL: PRESERVE FORMATTING (SNIPER MODE) & NOCLASSPATH
        // =========================================================
        
        // 1. Enable NoClasspath mode (robustness against missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        // 2. Enable Comments preservation
        launcher.getEnvironment().setCommentEnabled(true);

        // 3. Inject SniperJavaPrettyPrinter manually to guarantee strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // =========================================================

        launcher.addProcessor(new InitMethodProcessor());

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