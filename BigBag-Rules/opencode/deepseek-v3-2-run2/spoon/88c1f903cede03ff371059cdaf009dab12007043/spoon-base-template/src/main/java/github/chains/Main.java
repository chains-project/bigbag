package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

/**
 * Generic transformation rule for fixing package relocation breaking changes.
 * 
 * This rule handles cases where a class moves from a subpackage to its parent package.
 * Example: net.lingala.zip4j.core.ZipFile -> net.lingala.zip4j.ZipFile
 * 
 * The transformation is parameterized by:
 * - oldPackage: The old fully-qualified package (e.g., "net.lingala.zip4j.core")
 * - newPackage: The new package (e.g., "net.lingala.zip4j")
 * - className: The class name (e.g., "ZipFile")
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <sourceDir> <outputDir> <oldPackage> <newPackage> <className>");
            System.err.println("Example: java -cp . github.chains.Main /path/to/src /path/to/output net.lingala.zip4j.core net.lingala.zip4j ZipFile");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String oldPackage = args[2];
        String newPackage = args[3];
        String className = args[4];
        
        String oldFullyQualifiedName = oldPackage + "." + className;
        String newFullyQualifiedName = newPackage + "." + className;
        
        System.out.println("Transforming " + oldFullyQualifiedName + " -> " + newFullyQualifiedName);
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Add the processor
            launcher.addProcessor(new PackageRelocationProcessor(oldFullyQualifiedName, newFullyQualifiedName));
            
            // Run the transformation
            launcher.run();
            
            System.out.println("Transformation completed successfully.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor that updates type references and imports for a package relocation breaking change.
     */
    private static class PackageRelocationProcessor extends AbstractProcessor<CtCompilationUnit> {
        private final String oldFullyQualifiedName;
        private final String newFullyQualifiedName;
        
        public PackageRelocationProcessor(String oldFullyQualifiedName, String newFullyQualifiedName) {
            this.oldFullyQualifiedName = oldFullyQualifiedName;
            this.newFullyQualifiedName = newFullyQualifiedName;
        }
        
        @Override
        public void process(CtCompilationUnit compilationUnit) {
            // Process imports - remove old imports
            List<CtImport> imports = compilationUnit.getImports();
            for (int i = 0; i < imports.size(); i++) {
                CtImport imp = imports.get(i);
                if (imp.toString().contains(oldFullyQualifiedName)) {
                    // Remove old import
                    imp.delete();
                }
            }
            
            // Process type references in all types in the compilation unit
            for (CtType<?> type : compilationUnit.getDeclaredTypes()) {
                processTypeReferences(type);
            }
        }
        
        private void processTypeReferences(CtType<?> type) {
            // Get all type references in the type
            List<CtTypeReference<?>> typeRefs = type.getElements(e -> e instanceof CtTypeReference);
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName != null && qualifiedName.equals(oldFullyQualifiedName)) {
                    // Replace with new type reference
                    // Spoon will create the appropriate import when pretty-printing
                    CtTypeReference<?> newTypeRef = getFactory().createReference(newFullyQualifiedName);
                    typeRef.replace(newTypeRef);
                }
            }
        }
    }
}