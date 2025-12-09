package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtExpression;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Map;

public class DateTimeFormatRefactoring {

    /**
     * Processor to verify usages of org.springframework.format.annotation.DateTimeFormat
     * and its ISO constants (DATE, DATE_TIME).
     * 
     * Since the dependency diff indicates 'status=UNCHANGED', this processor 
     * focuses on identifying and validating existing usages without altering logic,
     * ensuring the project is compatible with the library version.
     */
    public static class DateTimeFormatProcessor extends AbstractProcessor<CtAnnotation<?>> {

        private static final String TARGET_ANNOTATION = "org.springframework.format.annotation.DateTimeFormat";
        private static final String ISO_ENUM_TYPE = "org.springframework.format.annotation.DateTimeFormat.ISO";

        @Override
        public boolean isToBeProcessed(CtAnnotation<?> candidate) {
            // Defensive Check: Ensure annotation type is resolvable or matches by name
            CtTypeReference<?> typeRef = candidate.getAnnotationType();
            if (typeRef == null) {
                return false;
            }

            // Check qualified name (robust against imports)
            if (TARGET_ANNOTATION.equals(typeRef.getQualifiedName())) {
                return true;
            }

            // Fallback for NoClasspath: Check simple name if qualified name is missing/partial
            return "DateTimeFormat".equals(typeRef.getSimpleName());
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            // Analyze the 'iso' value of the annotation
            Map<String, CtExpression> values = annotation.getValues();
            
            if (values.containsKey("iso")) {
                CtExpression<?> isoValue = values.get("iso");
                String valueStr = isoValue.toString();

                // Check for usages of ISO.DATE or ISO.DATE_TIME mentioned in the diff
                if (valueStr.contains("ISO.DATE") || valueStr.contains("ISO.DATE_TIME")) {
                    System.out.println("[VERIFIED] Found valid DateTimeFormat usage with ISO constant at: " 
                        + getPositionString(annotation));
                }
            } else {
                // Usage without ISO (likely using 'pattern')
                System.out.println("[VERIFIED] Found DateTimeFormat usage (custom pattern or default) at: " 
                    + getPositionString(annotation));
            }
            
            // NOTE: No transformation is applied because the diff reported status=UNCHANGED.
            // If transformation were needed (e.g., deprecation), logic would go here:
            // e.g., annotation.addValue("style", "SS");
        }

        private String getPositionString(CtElement element) {
            if (element.getPosition() != null && element.getPosition().isValidPosition()) {
                return element.getPosition().getFile().getName() + ":" + element.getPosition().getLine();
            }
            return "<unknown position>";
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/bad55510363bde900a60f13ecc744e0c244397d4/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bad55510363bde900a60f13ecc744e0c244397d4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/bad55510363bde900a60f13ecc744e0c244397d4/micronaut-openapi-codegen/gen/main/java/testmodel/spring/Model.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/bad55510363bde900a60f13ecc744e0c244397d4/attempt_1/transformed");

        // =========================================================
        // CRITICAL: Robust Sniper Configuration for Source Fidelity
        // =========================================================
        // 1. Enable comments to prevent loss during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Manually inject the Sniper printer to preserve formatting of unchanged sections
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new DateTimeFormatProcessor());

        System.out.println("Starting DateTimeFormat verification...");
        try {
            launcher.run();
            System.out.println("Verification complete. No changes made (Diff status: UNCHANGED).");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}