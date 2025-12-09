package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ClassPathResourceRefactoring {

    /**
     * Processor to migrate ClassPathResource constructors.
     * Strategy: Explicitly inject the Context ClassLoader if only the path is provided.
     * Old: new ClassPathResource("path/to/file.txt")
     * New: new ClassPathResource("path/to/file.txt", Thread.currentThread().getContextClassLoader())
     */
    public static class ClassPathResourceProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Check if the constructor belongs to ClassPathResource
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null || !typeRef.getQualifiedName().contains("org.springframework.core.io.ClassPathResource")) {
                return false;
            }

            // 2. Check Argument Count
            // We only want to refactor the single-argument constructor: ClassPathResource(String path)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Defensive Check for NoClasspath mode
            // Ensure the first argument looks like a path (String).
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();
            
            // If type is known and not String, skip (unlikely for ClassPathResource but good practice)
            if (argType != null && !argType.getQualifiedName().equals("java.lang.String")) {
                // In NoClasspath, argType might be null, so we proceed if null, strictly check if not null.
                return false;
            }

            return true;
        }

        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            Factory factory = getFactory();

            // Construct AST for: Thread.currentThread().getContextClassLoader()
            
            // 1. Reference to java.lang.Thread
            CtTypeReference<?> threadClassRef = factory.Type().createReference("java.lang.Thread");
            CtTypeAccess<?> threadTypeAccess = factory.Code().createTypeAccess(threadClassRef);

            // 2. Reference to Thread.currentThread()
            CtExecutableReference<?> currentThreadRef = factory.Method().createReference(
                    threadClassRef,
                    threadClassRef, // Return type is Thread
                    "currentThread"
            );

            // 3. Invocation: Thread.currentThread()
            CtInvocation<?> currentThreadInvocation = factory.Code().createInvocation(
                    threadTypeAccess,
                    currentThreadRef
            );

            // 4. Reference to java.lang.ClassLoader
            CtTypeReference<?> classLoaderRef = factory.Type().createReference("java.lang.ClassLoader");

            // 5. Reference to getContextClassLoader()
            CtExecutableReference<?> getContextClassLoaderRef = factory.Method().createReference(
                    threadClassRef,
                    classLoaderRef,
                    "getContextClassLoader"
            );

            // 6. Invocation: Thread.currentThread().getContextClassLoader()
            CtInvocation<?> contextClassLoaderInvocation = factory.Code().createInvocation(
                    currentThreadInvocation,
                    getContextClassLoaderRef
            );

            // Apply transformation: Add the new argument
            constructorCall.addArgument(contextClassLoaderInvocation);

            System.out.println("Refactored ClassPathResource constructor at line " + constructorCall.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/d7cc1744503dcd393468db7cbc47cec0cea2a2f0/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d7cc1744503dcd393468db7cbc47cec0cea2a2f0/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d7cc1744503dcd393468db7cbc47cec0cea2a2f0/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d7cc1744503dcd393468db7cbc47cec0cea2a2f0/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Sniper
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ClassPathResourceProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}