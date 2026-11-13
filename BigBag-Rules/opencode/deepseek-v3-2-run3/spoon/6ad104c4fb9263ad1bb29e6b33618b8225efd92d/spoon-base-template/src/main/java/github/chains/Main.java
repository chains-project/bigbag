package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic Spoon transformation for migrating types from an old package to a new package.
 * 
 * This transformation handles type references throughout the codebase.
 * Import statements will be automatically handled by Spoon's pretty printer
 * when we write the transformed code (with autoImports enabled).
 * 
 * The transformation is completely generic and parameterized:
 * - Source directory
 * - Old package prefix
 * - New package prefix
 * 
 * Usage: java github.chains.Main <source-directory> <old-package-prefix> <new-package-prefix>
 * Example: java github.chains.Main /path/to/src org.bouncycastle.crypto.tls org.bouncycastle.tls
 * 
 * Breaking Change Pattern:
 * - OLD: Types in package P.old.subpackage
 * - NEW: Types in package P.new.subpackage  
 * - TRANSFORMATION: Update all references from P.old.subpackage.X to P.new.subpackage.X
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldPackagePrefix = args[1];
        String newPackagePrefix = args[2];
        
        System.out.println("GENERIC PACKAGE MIGRATION TRANSFORMATION");
        System.out.println("=========================================");
        System.out.println("Source: " + sourceDir);
        System.out.println("Migration: " + oldPackagePrefix + " → " + newPackagePrefix);
        System.out.println();
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);  // Let Spoon handle imports
        launcher.getEnvironment().setNoClasspath(true);   // Don't need dependencies
        launcher.getEnvironment().setCommentEnabled(true); // Keep comments
        
        // Build model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Track changes
        Set<String> changedFiles = new HashSet<>();
        int typeRefUpdates = 0;
        
        // Find all type references
        List<CtTypeReference<?>> allTypeRefs = Query.getElements(factory, new TypeFilter<>(CtTypeReference.class));
        System.out.println("Scanning " + allTypeRefs.size() + " type references...");
        
        for (CtTypeReference<?> typeRef : allTypeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // Check if this reference needs updating
            if (qualifiedName.startsWith(oldPackagePrefix + ".") || qualifiedName.equals(oldPackagePrefix)) {
                // Get containing file for reporting
                CtType<?> containingType = typeRef.getParent(CtType.class);
                if (containingType != null) {
                    String fileName = containingType.getPosition().getFile() != null 
                        ? containingType.getPosition().getFile().getName() 
                        : "unknown";
                    changedFiles.add(fileName);
                }
                
                // Create new type reference with updated package
                String newQualifiedName = qualifiedName.replace(oldPackagePrefix, newPackagePrefix);
                CtTypeReference<?> newTypeRef = factory.Type().createReference(newQualifiedName);
                
                // Preserve type arguments if any
                if (typeRef.getActualTypeArguments() != null && !typeRef.getActualTypeArguments().isEmpty()) {
                    newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                }
                
                // Replace old reference with new one
                typeRef.replace(newTypeRef);
                typeRefUpdates++;
                
                if (typeRefUpdates <= 10) { // Show first 10 changes as examples
                    System.out.println("  Updated: " + qualifiedName + " → " + newQualifiedName);
                }
            }
        }
        
        // Print summary
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("TRANSFORMATION COMPLETE");
        System.out.println("=".repeat(60));
        System.out.println("Pattern: " + oldPackagePrefix + ".* → " + newPackagePrefix + ".*");
        System.out.println("Type references updated: " + typeRefUpdates);
        System.out.println("Files affected: " + changedFiles.size());
        
        if (typeRefUpdates > 0) {
            // Write transformed code
            String outputDir = sourceDir + "-migrated";
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println();
            System.out.println("Output written to: " + outputDir);
            System.out.println();
            System.out.println("NEXT STEPS:");
            System.out.println("1. Review changes in " + outputDir);
            System.out.println("2. Ensure new package " + newPackagePrefix + " is available in dependencies");
            System.out.println("3. Replace " + sourceDir + " with contents of " + outputDir);
            System.out.println("4. Compile and test");
            
            // Show example dependency addition for common cases
            if (oldPackagePrefix.equals("org.bouncycastle.crypto.tls") && 
                newPackagePrefix.equals("org.bouncycastle.tls")) {
                System.out.println();
                System.out.println("For BouncyCastle TLS migration, add to pom.xml:");
                System.out.println("<dependency>");
                System.out.println("  <groupId>org.bouncycastle</groupId>");
                System.out.println("  <artifactId>bctls-jdk15on</artifactId>");
                System.out.println("  <version>1.67</version>");
                System.out.println("</dependency>");
            }
        } else {
            System.out.println();
            System.out.println("No changes were needed.");
            System.out.println("The code may already use the new package structure.");
        }
        
        System.out.println("=".repeat(60));
    }
    
    private static void printUsage() {
        System.err.println("GENERIC PACKAGE MIGRATION TRANSFORMATION");
        System.err.println("=========================================");
        System.err.println();
        System.err.println("Purpose: Automatically migrate code from an old package to a new package.");
        System.err.println("This handles breaking changes where types move between packages.");
        System.err.println();
        System.err.println("Usage: java github.chains.Main <source-dir> <old-package> <new-package>");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  java github.chains.Main ./src org.bouncycastle.crypto.tls org.bouncycastle.tls");
        System.err.println("  java github.chains.Main ./src com.old.library.api com.new.library.v2.api");
        System.err.println("  java github.chains.Main ./src javax.xml.bind jakarta.xml.bind");
        System.err.println();
        System.err.println("The transformation will:");
        System.err.println("1. Update all type references in the code");
        System.err.println("2. Update import statements (via Spoon's auto-import)");
        System.err.println("3. Preserve code structure and comments");
        System.err.println();
        System.err.println("Note: You must update dependencies to include the new package.");
    }
}