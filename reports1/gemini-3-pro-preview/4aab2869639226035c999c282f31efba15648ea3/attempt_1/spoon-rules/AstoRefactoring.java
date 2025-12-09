package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Set;

public class AstoRefactoring {

    /**
     * Processor to handle API changes in com.artipie.asto:
     * 1. Renames 'StorageConfig' to 'Config' (as per method signature updates).
     * 2. Flags usages of the removed 'Storages' class.
     */
    public static class AstoMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        // Target types to detect
        private static final String OLD_CONFIG = "com.artipie.asto.factory.StorageConfig";
        private static final String OLD_FACTORY = "com.artipie.asto.factory.Storages";
        private static final Set<String> TARGETS = Set.of(OLD_CONFIG, OLD_FACTORY);

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            if (candidate == null) return false;
            
            // Defensive coding for NoClasspath: check Qualified Name carefully
            String qName = candidate.getQualifiedName();
            if (qName == null) return false;

            // Check for exact FQN or ending with expected class (ignoring package if unresolved)
            return TARGETS.stream().anyMatch(t -> qName.equals(t) || qName.endsWith("." + t.substring(t.lastIndexOf('.') + 1)));
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            String qName = ref.getQualifiedName();
            
            // Rule 1: Handle StorageConfig -> Config rename
            // This fixes method signatures like newStorage(StorageConfig) -> newStorage(Config)
            // and variable declarations.
            if (qName.contains("StorageConfig")) {
                ref.setSimpleName("Config");
                // Note: Package com.artipie.asto.factory remains the same, so no package update needed.
                System.out.println("Refactored: StorageConfig -> Config at line " + ref.getPosition().getLine());
            } 
            
            // Rule 2: Handle removal of Storages class
            // Since this class is completely removed and requires logic change (using specific factories),
            // we cannot auto-fix safely. We inject a FIXME comment.
            else if (qName.contains("Storages")) {
                CtElement parent = ref.getParent();
                if (parent != null) {
                    // Avoid duplicate comments if processed multiple times
                    boolean hasComment = parent.getComments().stream()
                        .anyMatch(c -> c.getContent().contains("FIXME: Class 'Storages' was removed"));
                    
                    if (!hasComment) {
                        parent.addComment(getFactory().Code().createComment(
                            "FIXME: Class 'Storages' was removed. Use a specific StorageFactory implementation (e.g., FileStorageFactory, S3StorageFactory).",
                            CtComment.CommentType.BLOCK
                        ));
                        System.out.println("Flagged: Removed class 'Storages' at line " + ref.getPosition().getLine());
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/4aab2869639226035c999c282f31efba15648ea3/http/src/main/java/com/artipie/security/policy/YamlPolicyFactory.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4aab2869639226035c999c282f31efba15648ea3/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4aab2869639226035c999c282f31efba15648ea3/http/src/main/java/com/artipie/security/policy/YamlPolicyFactory.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4aab2869639226035c999c282f31efba15648ea3/attempt_1/transformed");

        // 1. Enable comments to preserve existing documentation and allow adding new comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer to strictly preserve indentation and formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Configure NoClasspath mode (defensive assumption)
        launcher.getEnvironment().setNoClasspath(true);
        // Ignore duplicate declarations which might occur during partial resolution
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);

        launcher.addProcessor(new AstoMigrationProcessor());

        try {
            System.out.println("Starting ASTO Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}