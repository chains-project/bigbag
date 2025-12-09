import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JasyptPasswordEncoderRefactoring {

    /**
     * Processor to migrate removed Jasypt PasswordEncoder classes to the modern security4 implementation.
     * 
     * Addresses:
     * - org.jasypt.spring.security.PasswordEncoder (REMOVED)
     * - org.jasypt.spring.security2.PasswordEncoder (CONSTRUCTOR REMOVED / Deprecated flow)
     * - org.jasypt.spring.security3.PasswordEncoder (CONSTRUCTOR REMOVED / Deprecated flow)
     * 
     * Strategy:
     * Renames references from legacy packages to 'org.jasypt.spring.security4'.
     * This handles imports, variable declarations, and constructor calls (new PasswordEncoder()).
     */
    public static class PasswordEncoderProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final Set<String> TARGET_CLASSES = new HashSet<>(Arrays.asList(
            "org.jasypt.spring.security.PasswordEncoder",
            "org.jasypt.spring.security2.PasswordEncoder",
            "org.jasypt.spring.security3.PasswordEncoder"
        ));

        private static final String NEW_PACKAGE = "org.jasypt.spring.security4";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive: ensure candidate is not null
            if (candidate == null) {
                return false;
            }

            // In NoClasspath, getQualifiedName() attempts to resolve based on imports.
            String qName = candidate.getQualifiedName();
            
            // Check if this type reference matches one of the removed classes
            return TARGET_CLASSES.contains(qName);
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Create the new package reference
            CtPackage newPackage = getFactory().Package().getOrCreate(NEW_PACKAGE);
            
            // Update the package of the reference.
            // This updates both Import statements and fully qualified usages in code.
            // For simple name usages (e.g., "PasswordEncoder encoder"), changing the package 
            // of the underlying reference + the import reference keeps the code consistent.
            candidate.setPackage(newPackage);
            
            // Note: The simple name "PasswordEncoder" remains unchanged, which is correct
            // as the class name in security4 is also "PasswordEncoder".
            
            System.out.println("Refactored Jasypt PasswordEncoder reference at line " 
                + (candidate.getPosition().isValidPosition() ? candidate.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/9a8b6fc7847a0782ae4c48d0e4f7056507c0397d/geostore/src/core/security/src/main/java/it/geosolutions/geostore/core/security/password/GeoStoreDigestPasswordEncoder.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9a8b6fc7847a0782ae4c48d0e4f7056507c0397d/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9a8b6fc7847a0782ae4c48d0e4f7056507c0397d/geostore/src/core/security/src/main/java/it/geosolutions/geostore/core/security/password/GeoStoreDigestPasswordEncoder.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9a8b6fc7847a0782ae4c48d0e4f7056507c0397d/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Robust Refactoring
        
        // 1. Enable comments to preserve license headers and Javadocs
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. NoClasspath mode (we assume the library is missing/upgrading)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new PasswordEncoderProcessor());

        // Run transformation
        try {
            System.out.println("Starting Jasypt PasswordEncoder Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}