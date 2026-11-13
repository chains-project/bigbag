package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import java.util.List;

/**
 * Generic Spoon transformation for package migration.
 * Transforms all occurrences of types from old package to new package.
 * 
 * Usage: java -jar transformation.jar <source-directory> <old-package> <new-package>
 * Example: java -jar transformation.jar /path/to/project org.apache.struts2.dispatcher.ng.filter org.apache.struts2.dispatcher.filter
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar transformation.jar <source-directory> <old-package> <new-package>");
            System.out.println("Example: java -jar transformation.jar /path/to/project org.apache.struts2.dispatcher.ng.filter org.apache.struts2.dispatcher.filter");
            return;
        }
        
        String sourceDir = args[0];
        final String oldPackage = args[1];
        final String newPackage = args[2];
        
        System.out.println("Applying transformation: " + oldPackage + " -> " + newPackage);
        System.out.println("Source directory: " + sourceDir);
        
        final Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build model
        launcher.buildModel();
        
        // Create a scanner to transform all type references
        CtScanner scanner = new CtScanner() {
            @Override
            public void visitCtImport(CtImport ctImport) {
                super.visitCtImport(ctImport);
                
                CtReference ref = ctImport.getReference();
                if (ref instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                    String qualifiedName = typeRef.getQualifiedName();
                    if (qualifiedName != null && qualifiedName.startsWith(oldPackage)) {
                        String newQualifiedName = qualifiedName.replace(oldPackage, newPackage);
                        System.out.println("Transforming import: " + qualifiedName + " -> " + newQualifiedName);
                        
                        try {
                            CtTypeReference<?> newRef = launcher.getFactory().createReference(newQualifiedName);
                            ctImport.setReference(newRef);
                        } catch (Exception e) {
                            System.err.println("Error transforming import: " + e.getMessage());
                        }
                    }
                }
            }
            
            @Override
            public <T> void visitCtTypeReference(CtTypeReference<T> ref) {
                super.visitCtTypeReference(ref);
                
                String qualifiedName = ref.getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith(oldPackage)) {
                    String newQualifiedName = qualifiedName.replace(oldPackage, newPackage);
                    System.out.println("Transforming type reference: " + qualifiedName + " -> " + newQualifiedName);
                    
                    try {
                        CtTypeReference<T> newRef = launcher.getFactory().createReference(newQualifiedName);
                        ref.replace(newRef);
                    } catch (Exception e) {
                        System.err.println("Error transforming type reference: " + e.getMessage());
                    }
                }
            }
        };
        
        // Apply transformations
        launcher.getModel().getRootPackage().accept(scanner);
        
        // Write transformed files back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
}