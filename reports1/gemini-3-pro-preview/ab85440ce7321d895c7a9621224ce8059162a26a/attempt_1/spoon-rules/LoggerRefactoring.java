package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class LoggerRefactoring {

    /**
     * Processor to handle the breaking change:
     * com.legacy.Logger.log(String) -> com.legacy.Logger.log(LogMessage)
     */
    public static class LogWrapperProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"log".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 1) {
                return false;
            }

            // 3. Defensive Type Check (NoClasspath Mode)
            // We must decide if the argument needs wrapping.
            CtExpression<?> arg = args.get(0);
            CtTypeReference<?> argType = arg.getType();

            // If the type is resolved and is already the new type 'LogMessage', skip it.
            if (argType != null && argType.getQualifiedName().contains("LogMessage")) {
                return false;
            }

            // 4. Owner/Scope Check
            // We try to verify if this method belongs to 'Logger'.
            // In NoClasspath, declaring type might be null or unknown, so we use loose matching.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null 
                && !declaringType.getQualifiedName().contains("Logger") 
                && !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // Strategy: Wrap originalArg in LogMessage.of(...)
            
            // 1. Create reference to the new type: LogMessage
            CtTypeReference<?> logMessageRef = factory.Type().createReference("com.legacy.LogMessage");

            // 2. Create the static method reference: LogMessage.of(String)
            CtExecutableReference<?> ofMethodRef = factory.Method().createReference(
                logMessageRef,
                logMessageRef, // return type
                "of",
                factory.Type().stringType() // parameter type
            );

            // 3. Create the type access for the static call
            CtTypeAccess<?> typeAccess = factory.Code().createTypeAccess(logMessageRef);

            // 4. Create the wrapping invocation: LogMessage.of(originalArg)
            // Note: We use clone() on originalArg to detach it from the current tree before re-inserting
            CtInvocation<?> wrapper = factory.Code().createInvocation(
                typeAccess,
                ofMethodRef,
                originalArg.clone()
            );

            // 5. Replace the old argument with the wrapper
            originalArg.replace(wrapper);

            System.out.println("Refactored 'log' invocation at line: " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Configuration: Input/Output paths
        String inputPath = "/home/kth/Documents/last_transformer/output/ab85440ce7321d895c7a9621224ce8059162a26a/docker-adapter/src/test/java/com/artipie/docker/asto/AstoBlobsITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab85440ce7321d895c7a9621224ce8059162a26a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ab85440ce7321d895c7a9621224ce8059162a26a/docker-adapter/src/test/java/com/artipie/docker/asto/AstoBlobsITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ab85440ce7321d895c7a9621224ce8059162a26a/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: Sniper Printer Setup for Source Preservation
        // ==========================================================
        
        // 1. Preserve comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force usage of SniperJavaPrettyPrinter
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath mode (defensive handling of unknown types)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new LogWrapperProcessor());

        // Run
        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}