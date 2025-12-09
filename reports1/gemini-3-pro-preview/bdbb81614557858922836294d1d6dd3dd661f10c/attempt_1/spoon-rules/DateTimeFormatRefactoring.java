package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Map;

public class DateTimeFormatRefactoring {

    public static class DateTimeFormatProcessor extends AbstractProcessor<CtAnnotation<?>> {
        @Override
        public boolean isToBeProcessed(CtAnnotation<?> candidate) {
            // 1. Check Annotation Type (Defensive for NoClasspath)
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null || !typeRef.getQualifiedName().contains("DateTimeFormat")) {
                return false;
            }

            // 2. Check if 'iso' attribute is present
            Map<String, CtExpression> values = candidate.getValues();
            if (!values.containsKey("iso")) {
                return false;
            }

            // 3. Check if the value matches the targeted Enum constants
            // We use string matching to handle static imports and NoClasspath resolution safely
            String valueStr = values.get("iso").toString();
            return valueStr.contains("DATE") || valueStr.contains("DATE_TIME");
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            Factory factory = getFactory();
            Map<String, CtExpression> values = annotation.getValues();
            CtExpression<?> isoExpression = values.get("iso");
            String isoValueStr = isoExpression.toString();

            String replacementPattern = null;

            // Determine the pattern based on the Enum usage
            // Note: Check DATE_TIME first as it contains DATE
            if (isoValueStr.endsWith("DATE_TIME") || isoValueStr.contains("ISO.DATE_TIME")) {
                // ISO Date Time format
                replacementPattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
            } else if (isoValueStr.endsWith("DATE") || isoValueStr.contains("ISO.DATE")) {
                // ISO Date format
                replacementPattern = "yyyy-MM-dd";
            }

            if (replacementPattern != null) {
                // Remove the old 'iso' attribute
                values.remove("iso");

                // Add the new 'pattern' attribute
                // We directly modify the values map which updates the AST
                values.put("pattern", factory.createLiteral(replacementPattern));

                System.out.println("Refactored @DateTimeFormat at line " + annotation.getPosition().getLine() 
                    + ": Replaced iso=" + isoValueStr + " with pattern=\"" + replacementPattern + "\"");
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/bdbb81614557858922836294d1d6dd3dd661f10c/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bdbb81614557858922836294d1d6dd3dd661f10c/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/bdbb81614557858922836294d1d6dd3dd661f10c/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bdbb81614557858922836294d1d6dd3dd661f10c/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        // 1. Enable comments to preserve existing documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new DateTimeFormatProcessor());
        
        try { 
            launcher.run(); 
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}