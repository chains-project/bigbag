package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtComment;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Rule for Spring Context Migration.
 * 
 * ANALYSIS:
 * - org.springframework.context.ApplicationContext: UNCHANGED.
 * - org.springframework.context.ConfigurableApplicationContext: MODIFIED (Source Incompatible).
 * - method getBeanFactory(): UNCHANGED.
 * 
 * STRATEGY:
 * Since the diff indicates a generic "Source Incompatibility" for ConfigurableApplicationContext
 * without specifying a method removal or signature change (and explicitly states getBeanFactory is unchanged),
 * the automated strategy is to flag all declarations of `ConfigurableApplicationContext` 
 * for manual review to ensure the source incompatibility (e.g., interface changes) doesn't break usage.
 */
public class SpringRefactoring {

    public static class ContextReviewProcessor extends AbstractProcessor<CtVariable<?>> {

        @Override
        public boolean isToBeProcessed(CtVariable<?> candidate) {
            // 1. Defensive Type Check (NoClasspath)
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null) {
                return false;
            }

            // 2. Identify the target type (Relaxed matching)
            // matching "org.springframework.context.ConfigurableApplicationContext"
            String qName = typeRef.getQualifiedName();
            if (!qName.contains("ConfigurableApplicationContext")) {
                return false;
            }

            // 3. Skip if already flagged (prevent duplicate comments on re-runs)
            for (CtComment comment : candidate.getComments()) {
                if (comment.getContent().contains("Source Incompatible")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtVariable<?> variable) {
            // Action: Add a warning comment to the variable declaration.
            // This alerts the developer that this type has changed status to "Source Incompatible".
            
            String warningMsg = " TODO: Review dependency change. ConfigurableApplicationContext is marked Source Incompatible in the new version.";
            
            variable.addComment(getFactory().Code().createComment(
                warningMsg, 
                CtComment.CommentType.INLINE
            ));
            
            System.out.println("Flagged ConfigurableApplicationContext usage at " + variable.getPosition().toString());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/c5905f7220e1129a0448715ee5d0e61ee5ac31e1/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c5905f7220e1129a0448715ee5d0e61ee5ac31e1/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/c5905f7220e1129a0448715ee5d0e61ee5ac31e1/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c5905f7220e1129a0448715ee5d0e61ee5ac31e1/attempt_1/transformed");

        // CRITICAL SETTINGS for Robustness
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive mode for missing libraries
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ContextReviewProcessor());
        
        try {
            System.out.println("Starting Spring Context Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}