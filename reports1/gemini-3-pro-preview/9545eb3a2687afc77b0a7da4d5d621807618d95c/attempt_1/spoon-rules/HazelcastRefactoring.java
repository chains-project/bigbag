package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.HashMap;
import java.util.Map;

public class HazelcastRefactoring {

    /**
     * Processor to handle Hazelcast Package Migrations (Core -> Cluster).
     */
    public static class HazelcastPackageMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        private static final Map<String, String> PACKAGE_MIGRATIONS = new HashMap<>();

        static {
            // Mapping: Old Fully Qualified Name -> New Package Name
            PACKAGE_MIGRATIONS.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster");
            PACKAGE_MIGRATIONS.put("com.hazelcast.core.Member", "com.hazelcast.cluster");
            // MaxSizeConfig was removed in Hazelcast 4. It usually requires manual logic changes 
            // (replacing with EvictionConfig), so we do not blindly rename it to avoid logic errors.
        }

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding for NoClasspath:
            // 1. Candidate must not be null
            if (candidate == null) return false;
            
            // 2. Ignore primitives and null types
            if (candidate.isPrimitive()) return false;

            // 3. Robust Qualified Name check
            // We use string matching to avoid class loading issues in NoClasspath
            try {
                String qName = candidate.getQualifiedName();
                return PACKAGE_MIGRATIONS.containsKey(qName);
            } catch (Exception e) {
                // In case of unresolvable references in NoClasspath
                return false;
            }
        }

        @Override
        public void process(CtTypeReference<?> typeRef) {
            String oldQName = typeRef.getQualifiedName();
            String newPackageName = PACKAGE_MIGRATIONS.get(oldQName);

            if (newPackageName != null) {
                Factory factory = getFactory();
                
                // Update the package of the type reference
                // This updates imports and fully qualified usages automatically
                typeRef.setPackage(factory.Package().getOrCreate(newPackageName));
                
                System.out.println("Refactored: " + oldQName + " -> " + typeRef.getQualifiedName() 
                    + " at " + (typeRef.getPosition().isValidPosition() ? typeRef.getPosition().getLine() : "unknown line"));
            }
        }
    }

    public static void main(String[] args) {
        // Default configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/9545eb3a2687afc77b0a7da4d5d621807618d95c/openfire-hazelcast-plugin/src/java/org/jivesoftware/openfire/plugin/util/cache/ClusteredCacheFactory.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9545eb3a2687afc77b0a7da4d5d621807618d95c/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9545eb3a2687afc77b0a7da4d5d621807618d95c/openfire-hazelcast-plugin/src/java/org/jivesoftware/openfire/plugin/util/cache/ClusteredCacheFactory.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9545eb3a2687afc77b0a7da4d5d621807618d95c/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: SNIPER MODE & ENVIRONMENT CONFIGURATION
        // ==========================================================
        
        // 1. Enable comments to preserve license headers and Javadocs
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Enable NoClasspath mode (robustness against missing libs)
        launcher.getEnvironment().setNoClasspath(true);
        
        // 3. Force SniperJavaPrettyPrinter for high-fidelity source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // ==========================================================
        // PROCESSOR REGISTRATION
        // ==========================================================
        launcher.addProcessor(new HazelcastPackageMigrationProcessor());

        System.out.println("Starting Hazelcast Refactoring (Cluster/Member migration)...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}