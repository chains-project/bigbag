package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.CtModel;

import java.util.HashMap;
import java.util.Map;

public class Main {
    
    // Processor to handle package renames
    public static class PackageRenameProcessor extends AbstractProcessor<CtTypeReference<?>> {
        private final Map<String, String> packageMappings;
        
        public PackageRenameProcessor(Map<String, String> packageMappings) {
            this.packageMappings = packageMappings;
        }
        
        @Override
        public void process(CtTypeReference<?> typeRef) {
            String oldQualifiedName = typeRef.getQualifiedName();
            
            for (Map.Entry<String, String> entry : packageMappings.entrySet()) {
                String oldPackage = entry.getKey();
                String newPackage = entry.getValue();
                
                if (oldQualifiedName.startsWith(oldPackage + ".")) {
                    String simpleName = oldQualifiedName.substring(oldPackage.length() + 1);
                    String newQualifiedName = newPackage + "." + simpleName;
                    
                    try {
                        CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newQualifiedName);
                        typeRef.replace(newTypeRef);
                        System.out.println("Updated type reference: " + oldQualifiedName + " -> " + newQualifiedName);
                    } catch (Exception e) {
                        System.err.println("Warning: Could not update type reference for " + newQualifiedName);
                    }
                    break;
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            String qualifiedName = candidate.getQualifiedName();
            for (String oldPackage : packageMappings.keySet()) {
                if (qualifiedName.startsWith(oldPackage + ".")) {
                    return true;
                }
            }
            return false;
        }
    }
    
    // Processor to handle imports
    public static class ImportRenameProcessor extends AbstractProcessor<CtImport> {
        private final Map<String, String> packageMappings;
        
        public ImportRenameProcessor(Map<String, String> packageMappings) {
            this.packageMappings = packageMappings;
        }
        
        @Override
        public void process(CtImport ctImport) {
            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                String oldQualifiedName = typeRef.getQualifiedName();
                
                for (Map.Entry<String, String> entry : packageMappings.entrySet()) {
                    String oldPackage = entry.getKey();
                    String newPackage = entry.getValue();
                    
                    if (oldQualifiedName.startsWith(oldPackage + ".")) {
                        String simpleName = oldQualifiedName.substring(oldPackage.length() + 1);
                        String newQualifiedName = newPackage + "." + simpleName;
                        
                        try {
                            CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newQualifiedName);
                            // Create new import with updated reference
                            ctImport.setReference(newTypeRef);
                            System.out.println("Updated import: " + oldQualifiedName + " -> " + newQualifiedName);
                        } catch (Exception e) {
                            System.err.println("Warning: Could not update import for " + newQualifiedName);
                        }
                        break;
                    }
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtImport candidate) {
            if (candidate.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) candidate.getReference();
                String qualifiedName = typeRef.getQualifiedName();
                for (String oldPackage : packageMappings.keySet()) {
                    if (qualifiedName.startsWith(oldPackage + ".")) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java -cp spoon-base.jar github.chains.Main <source-dir> <output-dir> <old-package> <new-package>");
            System.err.println("Example: java -cp spoon-base.jar github.chains.Main /path/to/src /path/out \\");
            System.err.println("  \"com.google.api.services.cloudresourcemanager.model\" \\");
            System.err.println("  \"com.google.api.services.cloudresourcemanager.v3.model\"");
            System.err.println("\nFor multiple package renames, separate with commas:");
            System.err.println("Example: java -cp spoon-base.jar github.chains.Main /path/to/src /path/out \\");
            System.err.println("  \"old.pkg1,old.pkg2\" \"new.pkg1,new.pkg2\"");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String[] oldPackages = args[2].split(",");
        String[] newPackages = args[3].split(",");
        
        if (oldPackages.length != newPackages.length) {
            System.err.println("Error: Number of old packages must match number of new packages");
            System.exit(1);
        }
        
        Map<String, String> packageMappings = new HashMap<>();
        for (int i = 0; i < oldPackages.length; i++) {
            packageMappings.put(oldPackages[i].trim(), newPackages[i].trim());
        }
        
        System.out.println("Starting Spoon transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Package mappings:");
        for (Map.Entry<String, String> entry : packageMappings.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        
        try {
            transformProject(sourceDir, outputDir, packageMappings);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir, String outputDir, Map<String, String> packageMappings) {
        Launcher launcher = new Launcher();
        
        // Configure Spoon
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        
        // Add input source
        launcher.addInputResource(sourceDir);
        
        // Set output directory
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add processors
        launcher.addProcessor(new PackageRenameProcessor(packageMappings));
        launcher.addProcessor(new ImportRenameProcessor(packageMappings));
        
        // Run transformation
        launcher.run();
    }
}