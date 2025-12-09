package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.HashMap;
import java.util.Map;

public class DateTimeFormatRefactoring {

    /**
     * Processor to migrate usages of DateTimeFormat.ISO enum constants to explicit pattern strings.
     */
    public static class DateTimeFormatProcessor extends AbstractProcessor<CtAnnotation<?>> {

        @Override
        public boolean isToBeProcessed(CtAnnotation<?> annotation) {
            // 1. Check Annotation Type (Defensive String Matching)
            CtTypeReference<?> typeRef = annotation.getAnnotationType();
            if (typeRef == null || !typeRef.getQualifiedName().contains("DateTimeFormat")) {
                return false;
            }

            // 2. Check if "iso" attribute is present
            CtExpression<?> isoValue = annotation.getValue("iso");
            if (isoValue == null) {
                return false;
            }

            // 3. Check if the value refers to DATE or DATE_TIME
            // We use toString() to match the source code representation (e.g., "ISO.DATE" or "DateTimeFormat.ISO.DATE")
            // This is safer in NoClasspath mode than resolving the Enum type.
            String valueStr = isoValue.toString();
            return valueStr.contains("DATE") || valueStr.contains("DATE_TIME");
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            Factory factory = getFactory();
            CtExpression<?> isoValue = annotation.getValue("iso");
            String valueStr = isoValue.toString();

            // Determine the replacement pattern
            String newPattern = null;
            if (valueStr.endsWith("DATE")) {
                newPattern = "yyyy-MM-dd";
            } else if (valueStr.endsWith("DATE_TIME")) {
                // ISO-8601 standard format often implied by ISO.DATE_TIME
                newPattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
            }

            if (newPattern != null) {
                // Prepare the transformation
                Map<String, CtExpression> currentValues = new HashMap<>(annotation.getValues());
                
                // 1. Remove 'iso' argument
                currentValues.remove("iso");

                // 2. Add 'pattern' argument (only if not already present)
                if (!currentValues.containsKey("pattern")) {
                    CtLiteral<String> patternLiteral = factory.Code().createLiteral(newPattern);
                    currentValues.put("pattern", patternLiteral);
                }

                // 3. Apply changes
                // Note: setValues works well, but to preserve formatting of *other* unmodified arguments
                // with Sniper, we must rely on Spoon's internal handling of the modified map.
                annotation.setValues(currentValues);

                System.out.println("Refactored @DateTimeFormat at line " + 
                    (annotation.getPosition().isValidPosition() ? annotation.getPosition().getLine() : "unknown") + 
                    " | Replaced " + valueStr + " with pattern \"" + newPattern + "\"");
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/59849004763ffd66d14047d51908192ba0551a73/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/59849004763ffd66d14047d51908192ba0551a73/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/59849004763ffd66d14047d51908192ba0551a73/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/59849004763ffd66d14047d51908192ba0551a73/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode (defensive)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new DateTimeFormatProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}