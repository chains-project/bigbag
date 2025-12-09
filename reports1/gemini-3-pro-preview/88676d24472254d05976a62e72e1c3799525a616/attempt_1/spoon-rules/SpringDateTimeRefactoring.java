package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Map;

/**
 * Refactoring Rule for Spring DateTimeFormat.
 * 
 * Analysis of Diff:
 * The diff targets `org.springframework.format.annotation.DateTimeFormat$ISO` fields (DATE, DATE_TIME).
 * 
 * Refactoring Strategy:
 * Convert dependencies on the `ISO` enum constants within the `@DateTimeFormat` annotation
 * to explicit String patterns. This ensures date formatting behavior remains deterministic
 * regardless of changes to the underlying ISO enum or locale defaults in newer Spring versions.
 * 
 * Transformations:
 * 1. iso = ISO.DATE      -> pattern = "yyyy-MM-dd"
 * 2. iso = ISO.DATE_TIME -> pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
 */
public class SpringDateTimeRefactoring {

    public static class DateTimeFormatProcessor extends AbstractProcessor<CtAnnotation<?>> {

        @Override
        public boolean isToBeProcessed(CtAnnotation<?> annotation) {
            // 1. Target @DateTimeFormat annotations specifically
            CtTypeReference<?> typeRef = annotation.getAnnotationType();
            // Defensive check for nulls and match loosely for NoClasspath
            if (typeRef == null || !typeRef.getQualifiedName().endsWith("DateTimeFormat")) {
                return false;
            }

            // 2. Check if the 'iso' attribute is present
            if (!annotation.getValues().containsKey("iso")) {
                return false;
            }

            // 3. Check if the 'iso' value refers to the target Enums
            // We use string representation to handle both simple (ISO.DATE) and qualified names safely
            CtExpression<?> isoValue = annotation.getValue("iso");
            if (isoValue == null) return false;
            
            String valueStr = isoValue.toString();
            return valueStr.contains("ISO.DATE") || valueStr.contains("ISO.DATE_TIME");
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            CtExpression<?> isoValue = annotation.getValue("iso");
            String valueStr = isoValue.toString();
            String newPattern = null;

            // Determine the correct pattern
            // Note: Check DATE_TIME first as it contains the substring "DATE"
            if (valueStr.contains("ISO.DATE_TIME")) {
                // ISO 8601 Date Time format
                newPattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
            } else if (valueStr.contains("ISO.DATE")) {
                // ISO 8601 Date format
                newPattern = "yyyy-MM-dd";
            }

            if (newPattern != null) {
                // Modifying the annotation values
                Map<String, CtExpression> values = annotation.getValues();
                
                // 1. Remove the old 'iso' attribute
                values.remove("iso");
                
                // 2. Create the new 'pattern' literal
                CtExpression<String> patternLiteral = getFactory().Code().createLiteral(newPattern);
                
                // 3. Add 'pattern' attribute
                values.put("pattern", patternLiteral);
                
                // 4. Update the annotation
                annotation.setValues(values);

                System.out.println("Refactored @DateTimeFormat at line " + annotation.getPosition().getLine() 
                    + ": Replaced " + valueStr + " with pattern \"" + newPattern + "\"");
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/88676d24472254d05976a62e72e1c3799525a616/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/88676d24472254d05976a62e72e1c3799525a616/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/88676d24472254d05976a62e72e1c3799525a616/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/88676d24472254d05976a62e72e1c3799525a616/attempt_1/transformed");

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments to preserve existing code documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full compilation dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new DateTimeFormatProcessor());
        
        try {
            System.out.println("Starting Spring DateTimeFormat Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}