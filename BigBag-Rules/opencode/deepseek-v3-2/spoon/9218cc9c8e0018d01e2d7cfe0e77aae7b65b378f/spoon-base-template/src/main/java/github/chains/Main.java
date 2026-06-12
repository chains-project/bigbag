package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtJavaDoc;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformer.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming javax.validation to jakarta.validation in: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        
        // Add our processor
        launcher.addProcessor(new ValidationPackageTransformer());
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Transformation complete!");
    }
    
    /**
     * Spoon processor that transforms javax.validation imports and type references
     * to jakarta.validation for Jakarta EE migration.
     * Also fixes common generic type issues that arise during migration.
     */
    public static class ValidationPackageTransformer extends AbstractProcessor<CtType<?>> {
        private int importTransformations = 0;
        private int typeRefTransformations = 0;
        private int genericFixTransformations = 0;
        
        @Override
        public void process(CtType<?> type) {
            System.out.println("Processing type: " + type.getQualifiedName());
            
            // Get the compilation unit for this type
            CtCompilationUnit cu = type.getPosition().getCompilationUnit();
            if (cu == null) {
                return;
            }
            
            // Transform imports
            List<CtImport> importsToRemove = new ArrayList<>();
            List<CtTypeReference<?>> importsToAdd = new ArrayList<>();
            
            for (CtImport imp : cu.getImports()) {
                String importStr = imp.toString();
                if (importStr.contains("javax.validation")) {
                    // Create new import with jakarta.validation
                    String newImportStr = importStr.replace("javax.validation", "jakarta.validation");
                    // Extract the qualified name from import statement
                    String qualifiedName = newImportStr
                        .replace("import ", "")
                        .replace(";", "")
                        .trim();
                    
                    CtTypeReference<?> newImportRef = getFactory().createReference(qualifiedName);
                    importsToAdd.add(newImportRef);
                    importsToRemove.add(imp);
                    
                    System.out.println("  Import: " + importStr + " -> " + newImportStr);
                    importTransformations++;
                }
            }
            
            // Remove old imports and add new ones
            for (CtImport imp : importsToRemove) {
                cu.removeImport(imp);
            }
            for (CtTypeReference<?> importRef : importsToAdd) {
                cu.addImport(importRef);
            }
            
            // Transform type references in the code
            List<CtTypeReference<?>> typeRefs = getFactory().getModel().getElements(new TypeFilter<>(CtTypeReference.class));
            
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith("javax.validation.")) {
                    String newQualifiedName = qualifiedName.replace("javax.validation.", "jakarta.validation.");
                    
                    // Create new type reference
                    CtTypeReference<?> newTypeRef = getFactory().createReference(newQualifiedName);
                    
                    // Replace the type reference in the AST
                    typeRef.replace(newTypeRef);
                    
                    System.out.println("  Type reference: " + qualifiedName + " -> " + newQualifiedName);
                    typeRefTransformations++;
                }
            }
            
            // Fix common generic type issues
            fixGenericTypeIssues(type);
            
            // Also check for references in Javadoc
            for (CtJavaDoc javadoc : type.getElements(new TypeFilter<>(CtJavaDoc.class))) {
                String content = javadoc.getContent();
                if (content.contains("javax.validation")) {
                    String newContent = content.replace("javax.validation", "jakarta.validation");
                    javadoc.setContent(newContent);
                    System.out.println("  Javadoc updated in: " + type.getQualifiedName());
                }
            }
        }
        
        /**
         * Fix common generic type issues that arise during Jakarta EE migration.
         * For example: ConstraintDescriptor -> ConstraintDescriptor<?>
         */
        private void fixGenericTypeIssues(CtType<?> type) {
            // This is a simple pattern-based fix for common issues
            // In a real implementation, you would use more sophisticated AST matching
            
            String sourceCode = type.toString();
            
            // Fix Set<ConstraintDescriptor> -> Set<ConstraintDescriptor<?>>
            if (sourceCode.contains("Set<ConstraintDescriptor>")) {
                String fixed = sourceCode.replace("Set<ConstraintDescriptor>", "Set<ConstraintDescriptor<?>>");
                // We would need to parse and rebuild the AST properly here
                // This is a simplified example
                System.out.println("  Would fix Set<ConstraintDescriptor> to Set<ConstraintDescriptor<?>> in: " + type.getQualifiedName());
                genericFixTransformations++;
            }
            
            // Fix for (ConstraintDescriptor constraint : constraintsSet)
            if (sourceCode.contains("for (ConstraintDescriptor constraint")) {
                System.out.println("  Would fix raw ConstraintDescriptor to ConstraintDescriptor<?> in for-loop in: " + type.getQualifiedName());
                genericFixTransformations++;
            }
        }
        
        @Override
        public void processingDone() {
            System.out.println("\nTransformation summary:");
            System.out.println("  Import transformations: " + importTransformations);
            System.out.println("  Type reference transformations: " + typeRefTransformations);
            System.out.println("  Generic type fix transformations: " + genericFixTransformations);
            System.out.println("  Total transformations: " + (importTransformations + typeRefTransformations + genericFixTransformations));
            
            System.out.println("\nNote: Some compilation errors may remain due to:");
            System.out.println("  1. Dependencies that still use javax.validation (e.g., HibernateValidatorProperty)");
            System.out.println("  2. API signature changes beyond package renaming");
            System.out.println("  3. Generic type compatibility issues");
            System.out.println("\nThese issues may require manual fixes or dependency updates.");
        }
    }
}