package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.factory.Factory;

import java.util.HashSet;
import java.util.Set;

/**
 * A generic Spoon transformation for handling package rename breaking changes.
 * This transformation can be configured to rename packages and update all
 * references to classes from the old package to the new package.
 */
public class Main {
    
    /**
     * Configuration for a package rename transformation.
     */
    public static class PackageRenameConfig {
        private final String oldPackagePrefix;
        private final String newPackagePrefix;
        private final Set<String> classNamesToTransform;
        
        public PackageRenameConfig(String oldPackagePrefix, String newPackagePrefix) {
            this.oldPackagePrefix = oldPackagePrefix;
            this.newPackagePrefix = newPackagePrefix;
            this.classNamesToTransform = new HashSet<>();
        }
        
        public PackageRenameConfig addClass(String className) {
            classNamesToTransform.add(className);
            return this;
        }
        
        public PackageRenameConfig addAllClasses(String... classNames) {
            for (String className : classNames) {
                classNamesToTransform.add(className);
            }
            return this;
        }
        
        public boolean shouldTransformClass(String fullyQualifiedName) {
            if (classNamesToTransform.isEmpty()) {
                // If no specific classes are listed, transform all classes from the old package
                return fullyQualifiedName.startsWith(oldPackagePrefix);
            }
            
            // Check if this specific class should be transformed
            return classNamesToTransform.stream()
                .anyMatch(className -> fullyQualifiedName.equals(oldPackagePrefix + "." + className));
        }
        
        public String transformPackage(String oldPackage) {
            if (oldPackage.startsWith(oldPackagePrefix)) {
                return newPackagePrefix + oldPackage.substring(oldPackagePrefix.length());
            }
            return oldPackage;
        }
        
        public String transformTypeReference(String oldTypeReference) {
            if (oldTypeReference.startsWith(oldPackagePrefix)) {
                return newPackagePrefix + oldTypeReference.substring(oldPackagePrefix.length());
            }
            return oldTypeReference;
        }
    }
    
    /**
     * Main method to run the transformation.
     * 
     * @param args Command line arguments:
     *             args[0] = source directory to transform
     *             args[1] = output directory for transformed code
     *             args[2] = old package prefix (e.g., "develop.p2p.lib")
     *             args[3] = new package prefix (e.g., "tokyo.peya.lib")
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java -jar spoon-transformation.jar <sourceDir> <outputDir> <oldPackage> <newPackage>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/src /path/to/out develop.p2p.lib tokyo.peya.lib");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String oldPackage = args[2];
        String newPackage = args[3];
        
        System.out.println("Starting package rename transformation:");
        System.out.println("  Source directory: " + sourceDir);
        System.out.println("  Output directory: " + outputDir);
        System.out.println("  Old package: " + oldPackage);
        System.out.println("  New package: " + newPackage);
        
        // Create configuration - transform all classes from old package
        PackageRenameConfig config = new PackageRenameConfig(oldPackage, newPackage);
        
        // Apply the transformation
        applyPackageRenameTransformation(sourceDir, outputDir, config);
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Apply the package rename transformation to the given source directory.
     */
    public static void applyPackageRenameTransformation(String sourceDir, String outputDir, PackageRenameConfig config) {
        Launcher launcher = new Launcher();
        
        // Configure Spoon
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Create and apply the transformation visitor
        PackageRenameVisitor visitor = new PackageRenameVisitor(config, launcher.getFactory());
        model.getRootPackage().accept(visitor);
        
        // Generate transformed code
        launcher.prettyprint();
    }
    
    /**
     * Visitor that performs package rename transformations.
     */
    private static class PackageRenameVisitor extends CtScanner {
        private final PackageRenameConfig config;
        private final Factory factory;
        
        public PackageRenameVisitor(PackageRenameConfig config, Factory factory) {
            this.config = config;
            this.factory = factory;
        }
        
        @Override
        public void visitCtImport(CtImport ctImport) {
            // Get the imported reference
            CtReference reference = ctImport.getReference();
            
            if (reference instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) reference;
                String qualifiedName = typeRef.getQualifiedName();
                
                if (qualifiedName != null && config.shouldTransformClass(qualifiedName)) {
                    // Create new type reference with updated package
                    String newQualifiedName = config.transformTypeReference(qualifiedName);
                    
                    // Replace the import with new type reference
                    CtTypeReference<?> newTypeRef = factory.Type().createReference(newQualifiedName);
                    ctImport.setReference(newTypeRef);
                }
            }
            
            super.visitCtImport(ctImport);
        }
        
        @Override
        public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
            String qualifiedName = reference.getQualifiedName();
            
            if (qualifiedName != null && config.shouldTransformClass(qualifiedName)) {
                // Replace the type reference with new package
                String newQualifiedName = config.transformTypeReference(qualifiedName);
                CtTypeReference<T> newRef = factory.Type().createReference(newQualifiedName);
                
                // Replace in parent if it's a typed element
                CtElement parent = reference.getParent();
                if (parent != null && parent instanceof CtTypedElement) {
                    CtTypedElement<?> typedParent = (CtTypedElement<?>) parent;
                    if (typedParent.getType() != null && typedParent.getType().equals(reference)) {
                        typedParent.setType(newRef);
                    }
                }
            }
            
            super.visitCtTypeReference(reference);
        }
        
        @Override
        public <T> void visitCtField(CtField<T> field) {
            // Check field type
            CtTypeReference<T> fieldType = field.getType();
            if (fieldType != null) {
                String qualifiedName = fieldType.getQualifiedName();
                if (qualifiedName != null && config.shouldTransformClass(qualifiedName)) {
                    String newQualifiedName = config.transformTypeReference(qualifiedName);
                    CtTypeReference<T> newTypeRef = factory.Type().createReference(newQualifiedName);
                    field.setType(newTypeRef);
                }
            }
            
            super.visitCtField(field);
        }
        
        @Override
        public <T> void visitCtMethod(CtMethod<T> method) {
            // Check return type
            CtTypeReference<T> returnType = method.getType();
            if (returnType != null) {
                String returnQualifiedName = returnType.getQualifiedName();
                if (returnQualifiedName != null && config.shouldTransformClass(returnQualifiedName)) {
                    String newQualifiedName = config.transformTypeReference(returnQualifiedName);
                    CtTypeReference<T> newTypeRef = factory.Type().createReference(newQualifiedName);
                    method.setType(newTypeRef);
                }
            }
            
            // Check parameter types
            for (CtParameter<?> param : method.getParameters()) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType != null) {
                    String paramQualifiedName = paramType.getQualifiedName();
                    if (paramQualifiedName != null && config.shouldTransformClass(paramQualifiedName)) {
                        String newQualifiedName = config.transformTypeReference(paramQualifiedName);
                        CtTypeReference<?> newTypeRef = factory.Type().createReference(newQualifiedName);
                        param.setType(newTypeRef);
                    }
                }
            }
            
            super.visitCtMethod(method);
        }
        
        @Override
        public <T> void visitCtParameter(CtParameter<T> parameter) {
            // Check parameter type
            CtTypeReference<T> paramType = parameter.getType();
            if (paramType != null) {
                String qualifiedName = paramType.getQualifiedName();
                if (qualifiedName != null && config.shouldTransformClass(qualifiedName)) {
                    String newQualifiedName = config.transformTypeReference(qualifiedName);
                    CtTypeReference<T> newTypeRef = factory.Type().createReference(newQualifiedName);
                    parameter.setType(newTypeRef);
                }
            }
            
            super.visitCtParameter(parameter);
        }
    }
}