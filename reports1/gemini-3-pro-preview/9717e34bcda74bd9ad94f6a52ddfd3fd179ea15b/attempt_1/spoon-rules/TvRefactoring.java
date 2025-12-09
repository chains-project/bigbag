package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.HashMap;
import java.util.Map;

/**
 * Refactoring Strategy:
 * The class `com.jcabi.aspects.Tv` (Terminal Values) has been removed.
 * This class primarily served as a holder for common constant numbers (e.g., Tv.TEN, Tv.MILLION)
 * to avoid "Magic Number" checkstyle errors.
 *
 * Migration:
 * Detect usages of `Tv.FIELD_NAME` and replace them directly with their corresponding numeric literal.
 */
public class TvRefactoring {

    public static class TvProcessor extends AbstractProcessor<CtFieldRead<?>> {
        
        // Mapping of Tv constant names to their actual primitive values.
        private static final Map<String, Object> TV_CONSTANTS = new HashMap<>();
        
        static {
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
            TV_CONSTANTS.put("TWELVE", 12);
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
            TV_CONSTANTS.put("BILLION", 1000000000);
            TV_CONSTANTS.put("MINUS_ONE", -1);
        }

        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Safety check for variable reference
            if (candidate.getVariable() == null) return false;
            
            // 2. Safety check for declaring type (NoClasspath defensive coding)
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            if (declaringType == null) return false;

            // 3. Check if the field belongs to com.jcabi.aspects.Tv
            // Using equals on QualifiedName is generally safe here.
            // In NoClasspath, if the import is present, Spoon resolves the QName.
            return "com.jcabi.aspects.Tv".equals(declaringType.getQualifiedName());
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            String fieldName = fieldRead.getVariable().getSimpleName();
            
            if (TV_CONSTANTS.containsKey(fieldName)) {
                Object value = TV_CONSTANTS.get(fieldName);
                Factory factory = getFactory();
                
                // Create a literal (e.g., 10 or 1000)
                CtExpression<?> literal = factory.Code().createLiteral(value);
                
                // Replace the AST node (Tv.TEN) with the literal (10)
                fieldRead.replace(literal);
                
                System.out.println("Refactored Tv." + fieldName + " to literal " + value + 
                    " at line " + (fieldRead.getPosition().isValidPosition() ? fieldRead.getPosition().getLine() : "?"));
            } else {
                System.err.println("Warning: Encountered unknown Tv constant 'Tv." + fieldName + 
                    "'. Automatic replacement skipped to ensure safety.");
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/9717e34bcda74bd9ad94f6a52ddfd3fd179ea15b/jcabi-github/src/main/java/com/jcabi/github/mock/MkGithub.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9717e34bcda74bd9ad94f6a52ddfd3fd179ea15b/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9717e34bcda74bd9ad94f6a52ddfd3fd179ea15b/jcabi-github/src/main/java/com/jcabi/github/mock/MkGithub.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9717e34bcda74bd9ad94f6a52ddfd3fd179ea15b/attempt_1/transformed");

        // CRITICAL SETTINGS FOR SPOON 11+
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive mode for missing libraries
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TvProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}