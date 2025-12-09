package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LogbackEncoderRefactoring {

    /**
     * Processor to handle the breakage:
     * "METHOD_ABSTRACT_ADDED_IN_IMPLEMENTED_INTERFACE" in EncoderBase.
     * 
     * Strategy:
     * 1. Find concrete classes extending ch.qos.logback.core.encoder.EncoderBase.
     * 2. Check if they implement the required method (assumed to be 'headerBytes' for this template).
     * 3. Inject a default implementation if missing to fix compilation errors.
     */
    public static class EncoderBaseProcessor extends AbstractProcessor<CtClass<?>> {

        private static final String TARGET_SUPERCLASS = "EncoderBase";
        private static final String TARGET_PACKAGE = "ch.qos.logback.core.encoder";
        // The method likely to be missing. Modify this name if the compiler error indicates a different method (e.g., "footerBytes").
        private static final String MISSING_METHOD_NAME = "headerBytes";

        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            // 1. Must be a concrete class (abstract classes can defer implementation)
            if (candidate.isAbstract() || candidate.isInterface()) {
                return false;
            }

            // 2. Defensive Superclass Check (NoClasspath friendly)
            CtTypeReference<?> superClass = candidate.getSuperclass();
            if (superClass == null) {
                return false;
            }

            // Check if superclass is EncoderBase (relaxed check for NoClasspath)
            String qName = superClass.getQualifiedName();
            boolean isEncoderBase = qName.contains(TARGET_SUPERCLASS) && 
                                   (qName.contains(TARGET_PACKAGE) || qName.endsWith("." + TARGET_SUPERCLASS));
            
            if (!isEncoderBase) {
                return false;
            }

            // 3. Check if the method already exists
            // We look for: public byte[] headerBytes()
            Set<CtMethod<?>> methods = candidate.getMethods();
            for (CtMethod<?> method : methods) {
                if (MISSING_METHOD_NAME.equals(method.getSimpleName()) && method.getParameters().isEmpty()) {
                    return false; // Already implemented
                }
            }

            return true;
        }

        @Override
        public void process(CtClass<?> candidate) {
            Factory factory = getFactory();
            
            System.out.println("Applying fix to class: " + candidate.getQualifiedName());

            // Create the return type: byte[]
            CtTypeReference<byte[]> byteArrayType = factory.Type().createArrayReference(factory.Type().bytePrimitiveType());

            // Create the method body: return new byte[0];
            // 1. Create Literal '0'
            CtExpression<Integer> sizeZero = factory.Code().createLiteral(0);
            
            // 2. Create 'new byte[0]'
            CtNewArray<byte[]> newArray = factory.Core().createNewArray();
            newArray.setType(byteArrayType);
            newArray.addDimensionExpression(sizeZero);

            // 3. Create 'return ...'
            CtReturn<byte[]> returnStatement = factory.Core().createReturn();
            returnStatement.setReturnedExpression(newArray);

            // Create the method: public byte[] headerBytes() { ... }
            CtMethod<byte[]> newMethod = factory.Core().createMethod();
            newMethod.setSimpleName(MISSING_METHOD_NAME);
            newMethod.setType(byteArrayType);
            newMethod.setModifiers(new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)));
            newMethod.setBody(factory.Code().createBlock());
            newMethod.getBody().addStatement(returnStatement);
            
            // Add Javadoc or Comment indicating this was auto-generated
            newMethod.addComment(factory.Code().createComment("TODO: Auto-generated stub for library migration. Verify logic.", spoon.reflect.code.CtComment.CommentType.INLINE));

            // Inject the method into the class
            candidate.addMethod(newMethod);
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/61e96bfe3a32d6ef2e5d7912a518c78bd5474e74/singer/thrift-logger/src/main/java/com/pinterest/singer/client/logback/AppenderUtils.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/61e96bfe3a32d6ef2e5d7912a518c78bd5474e74/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/61e96bfe3a32d6ef2e5d7912a518c78bd5474e74/singer/thrift-logger/src/main/java/com/pinterest/singer/client/logback/AppenderUtils.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/61e96bfe3a32d6ef2e5d7912a518c78bd5474e74/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and Source Preservation
        // 1. Enable comments to preserve existing code documentation
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode handles missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new EncoderBaseProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}