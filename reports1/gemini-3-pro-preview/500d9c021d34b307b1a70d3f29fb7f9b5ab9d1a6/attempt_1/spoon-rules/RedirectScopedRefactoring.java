package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class RedirectScopedRefactoring {

    /**
     * Processor to migrate javax.mvc.RedirectScoped (Removed) to javax.enterprise.context.SessionScoped.
     * 
     * Rationale: The RedirectScoped annotation was removed from the Jakarta MVC specification. 
     * The standard Jakarta EE alternative to preserve state across a redirect (which involves a new HTTP request)
     * is the SessionScoped context.
     */
    public static class RedirectScopedProcessor extends AbstractProcessor<CtAnnotation<?>> {

        @Override
        public boolean isToBeProcessed(CtAnnotation<?> candidate) {
            CtTypeReference<?> typeRef = candidate.getAnnotationType();
            
            // Defensive: Check for null in NoClasspath scenarios
            if (typeRef == null) {
                return false;
            }

            String qualifiedName = typeRef.getQualifiedName();
            String simpleName = typeRef.getSimpleName();

            // Match exact fully qualified name OR simple name (if types are unresolved in NoClasspath)
            // We check if it is explicitly the removed javax.mvc.RedirectScoped
            boolean isTarget = "javax.mvc.RedirectScoped".equals(qualifiedName) 
                            || "RedirectScoped".equals(simpleName);
            
            if (!isTarget) {
                return false;
            }
            
            // Check if it's already the new type (idempotency check)
            if ("javax.enterprise.context.SessionScoped".equals(qualifiedName)) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtAnnotation<?> annotation) {
            // Create reference to the replacement scope: javax.enterprise.context.SessionScoped
            CtTypeReference<?> sessionScopedRef = getFactory().Type().createReference("javax.enterprise.context.SessionScoped");

            // Update the annotation type
            // In Sniper mode, if the import is missing, Spoon will likely print the Fully Qualified Name
            // to ensure compilation correctness.
            annotation.setAnnotationType(sessionScopedRef);

            System.out.println("Refactored @RedirectScoped to @SessionScoped at line " 
                + (annotation.getPosition().isValidPosition() ? annotation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/500d9c021d34b307b1a70d3f29fb7f9b5ab9d1a6/jakartaee-mvc-sample/src/main/java/com/example/web/AlertMessage.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/500d9c021d34b307b1a70d3f29fb7f9b5ab9d1a6/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/500d9c021d34b307b1a70d3f29fb7f9b5ab9d1a6/jakartaee-mvc-sample/src/main/java/com/example/web/AlertMessage.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/500d9c021d34b307b1a70d3f29fb7f9b5ab9d1a6/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Code Preservation
        // 1. Enable comments to preserve Javadoc/inline comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);
        // Disable auto-imports to let Sniper handle existing structure where possible, 
        // though replaced types may appear fully qualified.
        launcher.getEnvironment().setAutoImports(true); 

        launcher.addProcessor(new RedirectScopedProcessor());

        try {
            launcher.run();
            System.out.println("Migration finished. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}