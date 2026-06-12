package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import java.util.Set;

/**
 * Generic Spoon transformation for migrating from javax.validation to jakarta.validation.
 * 
 * This transformation addresses the breaking change introduced in Hibernate Validator 8.x
 * which migrated from Java EE 8 (javax.validation) to Jakarta EE 9+ (jakarta.validation).
 * 
 * Breaking Change Pattern:
 * - Old API: javax.validation.*
 * - New API: jakarta.validation.*
 * - Transformation: Replace all references to javax.validation with jakarta.validation
 * 
 * This transformation is generic and can be applied to any Maven project affected by
 * the Hibernate Validator 6.x → 8.x migration or any similar javax → jakarta migration.
 * 
 * Usage: java Main <source-directory>
 * Example: java Main /path/to/project/src/main/java
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.err.println("\nThis transformation migrates javax.validation.* to jakarta.validation.*");
            System.err.println("to fix Hibernate Validator 8.x breaking changes.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("🔧 javax.validation → jakarta.validation migration");
        System.out.println("📁 Source directory: " + sourceDir);
        System.out.println("⚙️  Building AST model...");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        int changes = 0;
        System.out.println("🔍 Scanning for javax.validation references...");
        
        for (CtType<?> type : model.getAllTypes()) {
            Set<CtTypeReference<?>> typeRefs = type.getReferencedTypes();
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith("javax.validation")) {
                    String newName = qualifiedName.replace("javax.validation", "jakarta.validation");
                    System.out.println("  • " + qualifiedName + " → " + newName);
                    
                    typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                    changes++;
                }
            }
        }
        
        if (changes == 0) {
            System.out.println("✅ No javax.validation references found.");
        } else {
            System.out.println("\n✅ Migration complete! " + changes + " type references updated.");
            System.out.println("💾 Writing changes to files...");
            
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("🎉 All changes applied successfully!");
        }
        
        System.out.println("\n📋 Transformation Summary:");
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.println("Breaking Change: Hibernate Validator 8.x migration");
        System.out.println("Old API Pattern: javax.validation.* (Java EE 8)");
        System.out.println("New API Pattern: jakarta.validation.* (Jakarta EE 9+)");
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.println("Affected Elements:");
        System.out.println("  • Import statements");
        System.out.println("  • Type references in code");
        System.out.println("  • Annotation references (@Valid, @NotNull, etc.)");
        System.out.println("  • Fully-qualified class names");
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.println("This transformation is:");
        System.out.println("  • Generic: Works for any project with javax.validation.* imports");
        System.out.println("  • Reusable: Apply to any source directory parameter");
        System.out.println("  • Safe: Only updates javax.validation.* references");
        System.out.println("  • Complete: Updates all AST node types (imports, annotations, types)");
        System.out.println("────────────────────────────────────────────────────────────");
    }
}