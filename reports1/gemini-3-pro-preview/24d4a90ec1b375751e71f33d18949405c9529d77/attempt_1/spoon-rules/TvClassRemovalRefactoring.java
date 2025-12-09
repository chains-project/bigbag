package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.HashMap;
import java.util.Map;

public class TvClassRemovalRefactoring {

    public static class TvRemovalProcessor extends AbstractProcessor<CtFieldRead<?>> {
        
        // Dictionary of common Tv constants from jcabi-aspects
        private static final Map<String, Object> CONSTANT_VALUES = new HashMap<>();
        
        static {
            CONSTANT_VALUES.put("ZERO", 0);
            CONSTANT_VALUES.put("ONE", 1);
            CONSTANT_VALUES.put("TWO", 2);
            CONSTANT_VALUES.put("THREE", 3);
            CONSTANT_VALUES.put("FOUR", 4);
            CONSTANT_VALUES.put("FIVE", 5);
            CONSTANT_VALUES.put("SIX", 6);
            CONSTANT_VALUES.put("SEVEN", 7);
            CONSTANT_VALUES.put("EIGHT", 8);
            CONSTANT_VALUES.put("NINE", 9);
            CONSTANT_VALUES.put("TEN", 10);
            CONSTANT_VALUES.put("TWENTY", 20);
            CONSTANT_VALUES.put("FIFTY", 50);
            CONSTANT_VALUES.put("SIXTY", 60);
            CONSTANT_VALUES.put("HUNDRED", 100);
            CONSTANT_VALUES.put("THOUSAND", 1000);
            CONSTANT_VALUES.put("MILLION", 1000000);
            CONSTANT_VALUES.put("BILLION", 1000000000); // Usually int in Tv
        }

        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Check if it's a static field access
            if (!candidate.isStatic()) {
                return false;
            }

            // 2. Identify the declaring class of the field
            // In NoClasspath, we rely on the reference info parsed from imports or fully qualified usage
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            
            if (declaringType == null) {
                return false;
            }

            String qualifiedName = declaringType.getQualifiedName();
            String simpleName = declaringType.getSimpleName();

            // 3. Match com.jcabi.aspects.Tv
            // We accept fully qualified matches or simple matches if imports are likely used
            boolean isTv = "com.jcabi.aspects.Tv".equals(qualifiedName) 
                        || "Tv".equals(simpleName);

            if (!isTv) {
                return false;
            }

            // 4. Check if we know the value of this constant
            String fieldName = candidate.getVariable().getSimpleName();
            return CONSTANT_VALUES.containsKey(fieldName);
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            Factory factory = getFactory();
            String fieldName = fieldRead.getVariable().getSimpleName();
            Object value = CONSTANT_VALUES.get(fieldName);

            if (value != null) {
                // Create a literal with the mapped value
                CtLiteral<?> literal = factory.Code().createLiteral(value);
                
                // Replace the Tv.CONST usage with the raw literal
                fieldRead.replace(literal);
                
                System.out.println("Refactored Tv." + fieldName + " to " + value + " at line " + fieldRead.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/24d4a90ec1b375751e71f33d18949405c9529d77/jcabi-s3/src/test/java/com/jcabi/s3/AwsOcketITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/24d4a90ec1b375751e71f33d18949405c9529d77/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/24d4a90ec1b375751e71f33d18949405c9529d77/jcabi-s3/src/test/java/com/jcabi/s3/AwsOcketITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/24d4a90ec1b375751e71f33d18949405c9529d77/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Code Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode to allow running without full dependency tree
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TvRemovalProcessor());
        
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}