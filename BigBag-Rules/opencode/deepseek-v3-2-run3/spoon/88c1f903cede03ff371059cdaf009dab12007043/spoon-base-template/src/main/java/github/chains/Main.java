package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Applies generic transformations for zip4j 2.x migration");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("Applying zip4j 2.x migration transformation to: " + sourceDir);
        
        // Check if source directory exists
        File sourceDirFile = new File(sourceDir);
        if (!sourceDirFile.exists()) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Create a backup before transformation
        String backupDir = sourceDir + "_backup";
        System.out.println("Creating backup at: " + backupDir);
        try {
            copyDirectory(sourceDirFile, new File(backupDir));
        } catch (Exception e) {
            System.err.println("Failed to create backup: " + e.getMessage());
            System.exit(1);
        }
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true); // Enable auto-imports to handle import updates
        launcher.getEnvironment().setShouldCompile(false);
        
        // Add all Java source files from the project
        launcher.addInputResource(sourceDir + "/src");
        
        try {
            CtModel model = launcher.buildModel();
            
            // Apply transformations for common breaking changes in zip4j 2.x
            // Transformation 1: Move ZipFile from core package to root package
            applyTransformation(launcher, model, "net.lingala.zip4j.core.ZipFile", "net.lingala.zip4j.ZipFile");
            
            // Write transformed code - output to same directory (in-place transformation)
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Original files backed up to: " + backupDir);
            
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Restoring from backup...");
            try {
                copyDirectory(new File(backupDir), sourceDirFile);
                System.err.println("Restored original files from backup");
            } catch (Exception ex) {
                System.err.println("Failed to restore from backup: " + ex.getMessage());
            }
            System.exit(1);
        }
    }
    
    // Generic transformation method that can be configured for different type migrations
    private static void applyTransformation(Launcher launcher, CtModel model, String oldType, String newType) {
        System.out.println("Applying transformation: " + oldType + " -> " + newType);
        int transformCount = 0;
        
        // Transform all type references in the code
        // When we replace type references, Spoon's auto-imports should update imports automatically
        List<CtTypeReference> typeRefs = model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class));
        for (CtTypeReference typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.equals(oldType)) {
                System.out.println("  Transforming type reference at position " + typeRef.getPosition() + ": " + oldType + " -> " + newType);
                CtTypeReference newTypeRef = launcher.getFactory().createReference(newType);
                typeRef.replace(newTypeRef);
                transformCount++;
            }
        }
        
        // Also handle imports explicitly (though auto-imports should handle this)
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class));
        for (CtImport importDecl : imports) {
            spoon.reflect.reference.CtReference ref = importDecl.getReference();
            if (ref instanceof CtTypeReference) {
                CtTypeReference importedType = (CtTypeReference) ref;
                String qualifiedName = importedType.getQualifiedName();
                if (qualifiedName != null && qualifiedName.equals(oldType)) {
                    System.out.println("  Transforming import: " + oldType + " -> " + newType);
                    importDecl.setReference(launcher.getFactory().createReference(newType));
                    transformCount++;
                }
            }
        }
        
        System.out.println("  Total transformations applied: " + transformCount);
    }
    
    private static void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdir();
            }
            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectory(new File(source, child), new File(target, child));
                }
            }
        } else {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}