package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Spoon Refactoring Tool generated for NoClasspath environments.
 * 
 * SCENARIO: 
 * Refactoring `LegacyService.process(arg)` -> `NewService.execute(arg)`
 * 
 * USAGE:
 * Adjust the Constants and the transformation logic in 'process()' to match
 * your specific dependency diff.
 */
public class BreakingChangeMigration {

    // --- CONFIGURATION ---
    private static final String TARGET_METHOD_NAME = "process";
    private static final String TARGET_CLASS_NAME = "LegacyService";
    private static final String REPLACEMENT_METHOD_NAME = "execute";
    private static final String REPLACEMENT_CLASS_NAME = "com.new.NewService";

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!TARGET_METHOD_NAME.equals(methodName)) {
                return false;
            }

            // 2. Check Owner Type (Defensive / Relaxed for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            // In NoClasspath, declaringType might be implicit or null in some edge cases, 
            // or return "<unknown>". We check if it contains our target name.
            if (declaringType != null 
                && !declaringType.getQualifiedName().contains(TARGET_CLASS_NAME)
                && !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Check Argument Count (Example: expecting 1 argument)
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 1) {
                return false;
            }

            // 4. Defensive Type Check (Crucial for NoClasspath)
            CtExpression<?> firstArg = args.get(0);
            CtTypeReference<?> argType = firstArg.getType();

            // If we are validating types, NEVER assume argType is not null.
            // Example: If we only want to process Strings:
            /*
            if (argType != null && !"java.lang.String".equals(argType.getQualifiedName())) {
                 // But be careful: in NoClasspath, complex types might not resolve fully.
                 // It is often safer to skip this check if the name/arg-count is strong enough.
            }
            */
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Clone arguments to preserve formatting of the parameters themselves
            List<CtExpression<?>> originalArgs = invocation.getArguments();
            CtExpression<?> arg0 = originalArgs.get(0).clone();

            // 1. Create Reference to the NEW Class
            CtTypeReference<?> newClassRef = factory.Type().createReference(REPLACEMENT_CLASS_NAME);

            // 2. Create Reference to the NEW Method
            // We use generic wildcards <?> to satisfy Java compiler
            CtExecutableReference<?> newMethodRef = factory.Method().createReference(
                newClassRef,
                factory.Type().voidPrimitiveType(),
                REPLACEMENT_METHOD_NAME,
                factory.Type().objectType() // Param type
            );

            // 3. Build the replacement Invocation: NewService.execute(arg0)
            // static invocation example:
            CtInvocation<?> newInvocation = factory.Code().createInvocation(
                factory.Code().createTypeAccess(newClassRef),
                newMethodRef,
                arg0
            );

            // 4. Apply Replacement
            invocation.replace(newInvocation);
            
            System.out.println("Refactored instance at line: " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Allow arguments to override paths, default to current directory
        String inputPath = "/home/kth/Documents/last_transformer/output/0ddd0efa29634a4783358cba727d0851236aa579/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/orbiter/OrbiterTokenManagerService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0ddd0efa29634a4783358cba727d0851236aa579/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0ddd0efa29634a4783358cba727d0851236aa579/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/orbiter/OrbiterTokenManagerService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0ddd0efa29634a4783358cba727d0851236aa579/attempt_1/transformed");

        // --- CRITICAL SNIPER CONFIGURATION ---
        // 1. Preserve Comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter for high-fidelity code preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath Mode (Assume libraries are missing)
        launcher.getEnvironment().setNoClasspath(true);

        // 4. Register Processor
        launcher.addProcessor(new MigrationProcessor());

        // 5. Run
        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}