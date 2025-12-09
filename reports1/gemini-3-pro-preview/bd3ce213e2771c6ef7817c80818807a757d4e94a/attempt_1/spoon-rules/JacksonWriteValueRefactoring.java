package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTry;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class JacksonWriteValueRefactoring {

    /**
     * Processor to handle breaking changes in Jackson's writeValue() methods.
     * The diff indicates that checked exceptions thrown by writeValue() have changed
     * (some removed, some added). This processor updates catch blocks for specific
     * Jackson exceptions (JsonGenerationException, JsonMappingException) which may 
     * no longer be valid, generalizing them to java.io.IOException or removing them 
     * if IOException is already caught.
     */
    public static class WriteValueProcessor extends AbstractProcessor<CtCatch> {
        @Override
        public boolean isToBeProcessed(CtCatch candidate) {
            // 1. Check the caught Exception Type (Defensive for NoClasspath)
            CtTypeReference<?> catchType = candidate.getParameter().getType();
            if (catchType == null) return false;
            
            String typeName = catchType.getQualifiedName();
            // Target specific Jackson exceptions that are often subject to change in writeValue
            boolean isTargetException = typeName.contains("JsonGenerationException") 
                                     || typeName.contains("JsonMappingException");
            
            if (!isTargetException) return false;

            // 2. Ensure the catch block belongs to a Try statement
            if (!(candidate.getParent() instanceof CtTry)) return false;
            CtTry parentTry = (CtTry) candidate.getParent();

            // 3. Scan the Try body for invocations of 'writeValue'
            List<CtInvocation<?>> invocations = parentTry.getBody().getElements(new TypeFilter<>(CtInvocation.class));
            
            boolean callsWriteValue = false;
            for (CtInvocation<?> inv : invocations) {
                String methodName = inv.getExecutable().getSimpleName();
                if ("writeValue".equals(methodName)) {
                    CtTypeReference<?> owner = inv.getExecutable().getDeclaringType();
                    // Defensive check: match owner name if known, or accept if unknown (NoClasspath)
                    if (owner == null 
                        || owner.getQualifiedName().contains("ObjectMapper") 
                        || owner.getQualifiedName().contains("ObjectWriter")
                        || owner.getQualifiedName().equals("<unknown>")) {
                        callsWriteValue = true;
                        break;
                    }
                }
            }
            
            return callsWriteValue;
        }

        @Override
        public void process(CtCatch catchBlock) {
            CtTry parentTry = (CtTry) catchBlock.getParent();
            
            // Check if java.io.IOException is already caught by a sibling catch block
            boolean ioExceptionHandled = false;
            for (CtCatch sibling : parentTry.getCatchers()) {
                if (sibling == catchBlock) continue;
                CtTypeReference<?> siblingType = sibling.getParameter().getType();
                // Check for IOException (handles both simple and qualified names)
                if (siblingType != null && siblingType.getQualifiedName().endsWith("IOException")) {
                    ioExceptionHandled = true;
                    break;
                }
            }

            if (ioExceptionHandled) {
                // SCENARIO A: IOException is already handled elsewhere.
                // Since the specific Jackson exception is no longer thrown (or is unchecked),
                // this catch block is likely dead code causing compilation errors.
                // We remove it.
                System.out.println("Removing redundant Jackson catch block at line " + catchBlock.getPosition().getLine());
                catchBlock.delete();
            } else {
                // SCENARIO B: IOException is NOT handled.
                // We generalize the specific Jackson exception to IOException to ensure
                // compatibility with the new method signature (which definitely throws IOException).
                System.out.println("Refactoring Jackson exception to IOException at line " + catchBlock.getPosition().getLine());
                
                CtTypeReference<?> ioExceptionRef = getFactory().Type().createReference("java.io.IOException");
                // Explicitly cast to raw type to satisfy Spoon generics
                catchBlock.getParameter().setType((CtTypeReference) ioExceptionRef);
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/bd3ce213e2771c6ef7817c80818807a757d4e94a/OCR4all/src/main/java/de/uniwue/helper/RecognitionHelper.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bd3ce213e2771c6ef7817c80818807a757d4e94a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/bd3ce213e2771c6ef7817c80818807a757d4e94a/OCR4all/src/main/java/de/uniwue/helper/RecognitionHelper.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bd3ce213e2771c6ef7817c80818807a757d4e94a/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode to handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new WriteValueProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}