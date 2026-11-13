package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation rule for fixing breaking dependency updates
 * caused by package renames in Maven projects.
 * 
 * This transformation identifies all occurrences of old package names
 * and replaces them with new package names.
 * 
 * The transformation is parameterized by:
 * - Old package pattern (e.g., "org.apache.struts2.dispatcher.ng.filter")
 * - New package pattern (e.g., "org.apache.struts2.dispatcher.filter")
 * 
 * Usage: java Main <source-directory> <old-package> <new-package>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java Main <source-directory> <old-package> <new-package>");
            System.err.println("Example: java Main /path/to/project org.apache.struts2.dispatcher.ng.filter org.apache.struts2.dispatcher.filter");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldPackage = args[1];
        String newPackage = args[2];
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transforming: " + oldPackage + " -> " + newPackage);
        
        Launcher launcher = new Launcher();
        // Process all Java files in the project
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        // Don't copy resources, only output transformed Java files
        launcher.getEnvironment().setShouldCompile(false);
        
        CtModel model = launcher.buildModel();
        
        int fixCount = 0;
        
        // Find all type references in the code (including in imports)
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                String qualifiedName = typeRef.getQualifiedName();
                return qualifiedName != null && qualifiedName.contains(oldPackage);
            }
        });
        
        System.out.println("Found " + typeRefs.size() + " type references to fix");
        
        // Fix each type reference
        for (CtTypeReference<?> typeRef : typeRefs) {
            String oldQualifiedName = typeRef.getQualifiedName();
            String newQualifiedName = oldQualifiedName.replace(oldPackage, newPackage);
            
            System.out.println("Transforming type reference: " + oldQualifiedName + " -> " + newQualifiedName);
            
            // Create new type reference
            CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newQualifiedName);
            typeRef.replace(newTypeRef);
            fixCount++;
        }
        
        if (fixCount > 0) {
            // Write changes back to source directory
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Transformation complete! Fixed " + fixCount + " occurrences.");
        } else {
            System.out.println("No transformations needed.");
        }
    }
}