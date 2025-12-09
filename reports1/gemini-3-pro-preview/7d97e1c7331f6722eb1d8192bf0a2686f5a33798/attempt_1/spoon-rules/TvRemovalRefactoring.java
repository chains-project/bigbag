package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.HashMap;
import java.util.Map;

public class TvRemovalRefactoring {

    public static class TvProcessor extends AbstractProcessor<CtFieldRead<?>> {

        private static final Map<String, Object> TV_CONSTANTS = new HashMap<>();

        static {
            // Population based on common constants in com.jcabi.aspects.Tv
            TV_CONSTANTS.put("ZERO", 0);
            TV_CONSTANTS.put("ONE", 1);
            TV_CONSTANTS.put("TWO", 2);
            TV_CONSTANTS.put("THREE", 3);
            TV_CONSTANTS.put("FOUR", 4);
            TV_CONSTANTS.put("FIVE", 5);
            TV_CONSTANTS.put("SIX", 6);
            TV_CONSTANTS.put("SEVEN", 7);
            TV_CONSTANTS.put("EIGHT", 8);
            TV_CONSTANTS.put("NINE", 9);
            TV_CONSTANTS.put("TEN", 10);
            TV_CONSTANTS.put("TWENTY", 20);
            TV_CONSTANTS.put("THIRTY", 30);
            TV_CONSTANTS.put("FORTY", 40);
            TV_CONSTANTS.put("FIFTY", 50);
            TV_CONSTANTS.put("SIXTY", 60);
            TV_CONSTANTS.put("SEVENTY", 70);
            TV_CONSTANTS.put("EIGHTY", 80);
            TV_CONSTANTS.put("NINETY", 90);
            TV_CONSTANTS.put("HUNDRED", 100);
            TV_CONSTANTS.put("THOUSAND", 1000);
            TV_CONSTANTS.put("MILLION", 1000000);
            TV_CONSTANTS.put("BILLION", 1000000000L); // Defined as long in Tv
            TV_CONSTANTS.put("TRILLION", 1000000000000L); // Defined as long in Tv
        }

        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            CtFieldReference<?> variable = candidate.getVariable();
            if (variable == null) {
                return false;
            }

            // 1. Check Field Name
            String fieldName = variable.getSimpleName();
            if (!TV_CONSTANTS.containsKey(fieldName)) {
                return false;
            }

            // 2. Check Declaring Type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = variable.getDeclaringType();
            
            // If we have type info
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                return qName.contains("com.jcabi.aspects.Tv");
            }

            // Fallback: Check if the qualified name of the variable itself contains the path
            // This handles static imports or cases where declaring type isn't fully resolved
            return variable.getQualifiedName().contains("com.jcabi.aspects.Tv");
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            String fieldName = fieldRead.getVariable().getSimpleName();
            Object value = TV_CONSTANTS.get(fieldName);

            // Create a literal with the mapped value
            CtLiteral<?> literal = getFactory().Code().createLiteral(value);

            // Replace the Tv.CONSTANT accessor with the actual value
            fieldRead.replace(literal);
            
            System.out.println("Refactored Tv." + fieldName + " to literal " + value + " at " + fieldRead.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/7d97e1c7331f6722eb1d8192bf0a2686f5a33798/jcabi-simpledb/src/test/java/com/jcabi/simpledb/RegionITCase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7d97e1c7331f6722eb1d8192bf0a2686f5a33798/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/7d97e1c7331f6722eb1d8192bf0a2686f5a33798/jcabi-simpledb/src/test/java/com/jcabi/simpledb/RegionITCase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7d97e1c7331f6722eb1d8192bf0a2686f5a33798/attempt_1/transformed");

        // CRITICAL SETTINGS
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode since the dependency is removed/missing
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TvProcessor());
        
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}