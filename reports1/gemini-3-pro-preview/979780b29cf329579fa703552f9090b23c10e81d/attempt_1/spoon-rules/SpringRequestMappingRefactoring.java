package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SpringRequestMappingRefactoring {

    /**
     * Processor to migrate RequestMappingInfo.getPatternsCondition() to getPathPatternsCondition().
     * 
     * Breaking Change: Spring 6 removed getPatternsCondition().
     * Fix: Rename to getPathPatternsCondition() (assuming PathPattern usage).
     */
    public static class RequestMappingInfoProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // We are looking for "getPatternsCondition" which was removed.
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"getPatternsCondition".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check
            // getter should have 0 arguments
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner Check (Defensive for NoClasspath)
            // We check if the method belongs to RequestMappingInfo or a likely variable of that type.
            CtExecutableReference<?> executable = candidate.getExecutable();
            CtTypeReference<?> declaringType = executable.getDeclaringType();

            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Check for strict name or substring match if resolution fails
                if (!qualifiedName.contains("RequestMappingInfo") && 
                    !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }
            
            // If NoClasspath mode leaves DeclaringType completely null, 
            // we rely on the method name uniqueness (getPatternsCondition is fairly specific to Spring MVC).
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method invocation
            // getPatternsCondition() -> getPathPatternsCondition()
            
            // Note: This assumes the return type usage downstream is compatible 
            // or will be inferred by the compiler (e.g. var usage or direct chaining).
            invocation.getExecutable().setSimpleName("getPathPatternsCondition");
            
            System.out.println("Refactored RequestMappingInfo.getPatternsCondition() at line " 
                + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/979780b29cf329579fa703552f9090b23c10e81d/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979780b29cf329579fa703552f9090b23c10e81d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/979780b29cf329579fa703552f9090b23c10e81d/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/EndpointService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/979780b29cf329579fa703552f9090b23c10e81d/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Transformation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new RequestMappingInfoProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}