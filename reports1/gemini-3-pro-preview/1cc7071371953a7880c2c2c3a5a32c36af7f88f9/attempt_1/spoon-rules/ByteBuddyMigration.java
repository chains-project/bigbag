package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ByteBuddyMigration {

    /**
     * Processor to migrate from AssertJ's internal shaded ByteBuddy to the official ByteBuddy library.
     * 
     * Analysis:
     * The diff indicates that classes in 'org.assertj.core.internal.bytebuddy' were removed.
     * This suggests that AssertJ has stopped exposing its internal shaded copy of ByteBuddy.
     * The strategy is to relocate all references from the internal package to the official 'net.bytebuddy' package.
     */
    public static class ByteBuddyRelocationProcessor extends AbstractProcessor<CtElement> {
        private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
        private static final String NEW_PREFIX = "net.bytebuddy";

        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            // 1. Check TypeReferences (e.g., variable declarations, extends, implements, single imports)
            if (candidate instanceof CtTypeReference) {
                CtTypeReference<?> ref = (CtTypeReference<?>) candidate;
                // Defensive check: primitives or unknown types might have null packages
                return ref.getPackage() != null && 
                       ref.getPackage().getQualifiedName().startsWith(OLD_PREFIX);
            }
            
            // 2. Check PackageReferences (specifically for wildcard imports: import org.assertj.core.internal.bytebuddy.*;)
            if (candidate instanceof CtPackageReference) {
                CtPackageReference ref = (CtPackageReference) candidate;
                return ref.getQualifiedName().startsWith(OLD_PREFIX);
            }
            return false;
        }

        @Override
        public void process(CtElement candidate) {
            if (candidate instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) candidate;
                CtPackageReference oldPackRef = typeRef.getPackage();
                
                String oldPackName = oldPackRef.getQualifiedName();
                String newPackName = oldPackName.replace(OLD_PREFIX, NEW_PREFIX);
                
                // Update the package of the TypeReference.
                // In Spoon, modifying the package reference of a type updates its qualified name
                // and should reflect in printed imports and usage.
                typeRef.setPackage(getFactory().Package().createReference(newPackName));
                
                System.out.println("Relocated Type: " + typeRef.getQualifiedName());
            } 
            else if (candidate instanceof CtPackageReference) {
                CtPackageReference packRef = (CtPackageReference) candidate;
                String oldPackName = packRef.getQualifiedName();
                String newPackName = oldPackName.replace(OLD_PREFIX, NEW_PREFIX);
                
                // For PackageReferences (like in wildcard imports), we construct a new reference.
                CtPackageReference newRef = getFactory().Package().createReference(newPackName);
                
                try {
                    // Attempt to replace the AST node directly
                    candidate.replace(newRef);
                    System.out.println("Relocated Package: " + newPackName);
                } catch (Exception e) {
                    // Fallback: if replacement fails (e.g., due to parent hierarchy), mutate the name
                    packRef.setSimpleName(newPackName);
                }
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/1cc7071371953a7880c2c2c3a5a32c36af7f88f9/assertj-vavr/src/main/java/org/assertj/vavr/api/ClassLoadingStrategyFactory.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1cc7071371953a7880c2c2c3a5a32c36af7f88f9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1cc7071371953a7880c2c2c3a5a32c36af7f88f9/assertj-vavr/src/main/java/org/assertj/vavr/api/ClassLoadingStrategyFactory.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1cc7071371953a7880c2c2c3a5a32c36af7f88f9/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to prevent loss during processing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer to strictly preserve indentation and unmodified code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode as the 'org.assertj' internals are likely missing from the user's classpath
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ByteBuddyRelocationProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}