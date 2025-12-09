package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.HashMap;
import java.util.Map;

public class HazelcastRefactoring {

    /**
     * Processor to handle the migration of removed Hazelcast classes.
     * Based on the diff, these classes were removed. In the context of Hazelcast 3 -> 4 migration:
     * 1. com.hazelcast.core.Cluster moved to com.hazelcast.cluster.Cluster
     * 2. com.hazelcast.core.Member moved to com.hazelcast.cluster.Member
     * 3. com.hazelcast.config.MaxSizeConfig was removed and superseded by EvictionConfig.
     */
    public static class HazelcastMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final Map<String, String> TYPE_MAPPINGS = new HashMap<>();

        static {
            // Package migrations (Class moved)
            TYPE_MAPPINGS.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
            TYPE_MAPPINGS.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
            
            // Class replacement (Class removed -> closest semantic replacement)
            // Note: This changes the type, but methods on this type might still need manual adjustment.
            TYPE_MAPPINGS.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig");
        }

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive checks for NoClasspath environment
            if (candidate == null || candidate.isPrimitive()) {
                return false;
            }

            // We rely on Qualified Name matching to identify the types to refactor.
            // In NoClasspath, getQualifiedName() returns what is available from source.
            String qName = candidate.getQualifiedName();
            return TYPE_MAPPINGS.containsKey(qName);
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            String oldFQN = ref.getQualifiedName();
            String newFQN = TYPE_MAPPINGS.get(oldFQN);

            if (newFQN != null && !newFQN.equals(oldFQN)) {
                // 1. Parse the new Fully Qualified Name
                int lastDotIndex = newFQN.lastIndexOf('.');
                String newPackageName = (lastDotIndex == -1) ? "" : newFQN.substring(0, lastDotIndex);
                String newSimpleName = (lastDotIndex == -1) ? newFQN : newFQN.substring(lastDotIndex + 1);

                // 2. Update the Simple Name
                ref.setSimpleName(newSimpleName);

                // 3. Update the Package Reference
                // We create a new reference to ensure clean separation from the old package object
                if (!newPackageName.isEmpty()) {
                    CtPackageReference newPkgRef = getFactory().Package().createReference(newPackageName);
                    ref.setPackage(newPkgRef);
                }

                System.out.println("Refactored: " + oldFQN + " -> " + newFQN + " (Line: " + ref.getPosition().getLine() + ")");
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded as needed)
        String inputPath = "/home/kth/Documents/last_transformer/output/2b4d49d68112941b8abb818549389709d8327963/openfire-hazelcast-plugin/src/java/org/jivesoftware/openfire/plugin/util/cache/ClusteredCacheFactory.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/2b4d49d68112941b8abb818549389709d8327963/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/2b4d49d68112941b8abb818549389709d8327963/openfire-hazelcast-plugin/src/java/org/jivesoftware/openfire/plugin/util/cache/ClusteredCacheFactory.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/2b4d49d68112941b8abb818549389709d8327963/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and refactoring preservation
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter to preserve original formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath to allow running without full dependency JARs
        launcher.getEnvironment().setNoClasspath(true);

        // Add the migration processor
        launcher.addProcessor(new HazelcastMigrationProcessor());

        try {
            System.out.println("Starting Hazelcast Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}