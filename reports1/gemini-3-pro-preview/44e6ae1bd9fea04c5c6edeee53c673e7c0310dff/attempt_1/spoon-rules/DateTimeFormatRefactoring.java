package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring rule for org.springframework.format.annotation.DateTimeFormat.ISO fields.
 * 
 * Based on the diff, the fields are status=UNCHANGED.
 * This processor identifies usages of ISO.DATE and ISO.DATE_TIME.
 * Currently configured as a detector/validator.
 */
public class DateTimeFormatRefactoring {

    public static class DateTimeFormatIsoProcessor extends AbstractProcessor<CtFieldRead<?>> {

        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Get the field reference
            CtFieldReference<?> fieldRef = candidate.getVariable();
            if (fieldRef == null) {
                return false;
            }

            // 2. Check Field Name (DATE or DATE_TIME)
            String fieldName = fieldRef.getSimpleName();
            if (!"DATE".equals(fieldName) && !"DATE_TIME".equals(fieldName)) {
                return false;
            }

            // 3. Check Declaring Type (Defensive for NoClasspath)
            // Usage is typically: DateTimeFormat.ISO.DATE
            CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
            
            // In NoClasspath, declaringType might be partial, so we check qualified name safely.
            if (declaringType == null) {
                return false;
            }
            
            String qualifiedName = declaringType.getQualifiedName();
            // Match 'org.springframework.format.annotation.DateTimeFormat$ISO' or generic 'DateTimeFormat.ISO'
            // We use .contains to handle cases where package prefix might be unresolved or inferred.
            boolean isTargetType = qualifiedName.contains("DateTimeFormat") 
                                && (qualifiedName.contains("$ISO") || qualifiedName.contains(".ISO"));

            return isTargetType;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            // Logic to process the found usage.
            // Since the diff indicates 'UNCHANGED', we simply log the detection.
            // If a transformation were required (e.g., renaming to ISO.DATE_V2), 
            // it would be implemented here using fieldRead.replace(...).
            
            System.out.println("Found usage of DateTimeFormat.ISO." + fieldRead.getVariable().getSimpleName() 
                             + " at " + fieldRead.getPosition());
            
            /* 
             * EXAMPLE TRANSFORMATION (Commented out):
             * 
             * Factory factory = getFactory();
             * // Create reference to new enum or type
             * CtTypeReference<?> newType = factory.Type().createReference("java.time.format.FormatStyle");
             * CtExpression<?> replacement = factory.Code().createCodeSnippetExpression("FormatStyle.SHORT");
             * fieldRead.replace(replacement);
             */
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/44e6ae1bd9fea04c5c6edeee53c673e7c0310dff/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/44e6ae1bd9fea04c5c6edeee53c673e7c0310dff/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/44e6ae1bd9fea04c5c6edeee53c673e7c0310dff/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/44e6ae1bd9fea04c5c6edeee53c673e7c0310dff/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new DateTimeFormatIsoProcessor());
        
        try {
            System.out.println("Starting refactoring analysis...");
            launcher.run();
            System.out.println("Analysis complete. Check output directory for any changes.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}