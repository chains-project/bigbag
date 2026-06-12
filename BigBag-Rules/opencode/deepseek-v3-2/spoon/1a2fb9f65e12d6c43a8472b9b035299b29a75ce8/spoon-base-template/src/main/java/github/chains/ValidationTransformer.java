package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import java.util.List;

/**
 * A Spoon processor that transforms javax.validation references to jakarta.validation.
 * This is a generic transformation that can be applied to any project affected by
 * the javax.validation to jakarta.validation namespace migration.
 */
public class ValidationTransformer extends AbstractProcessor<CtTypeReference<?>> {
    
    @Override
    public boolean isToBeProcessed(CtTypeReference<?> candidate) {
        if (candidate == null || candidate.getQualifiedName() == null) {
            return false;
        }
        return candidate.getQualifiedName().startsWith("javax.validation.");
    }
    
    @Override
    public void process(CtTypeReference<?> typeRef) {
        String oldQualifiedName = typeRef.getQualifiedName();
        String newQualifiedName = oldQualifiedName.replace("javax.validation.", "jakarta.validation.");
        
        try {
            // Create new type reference
            CtTypeReference<?> newTypeRef = getFactory().createReference(newQualifiedName);
            
            // Copy important properties
            if (typeRef.getAnnotations() != null) {
                newTypeRef.setAnnotations(typeRef.getAnnotations());
            }
            
            // Replace the old reference with the new one
            typeRef.replace(newTypeRef);
            
            System.out.println("Transformed: " + oldQualifiedName + " -> " + newQualifiedName);
        } catch (Exception e) {
            System.err.println("Failed to transform " + oldQualifiedName + ": " + e.getMessage());
        }
    }
    
    /**
     * Also process imports separately since they might not be caught by type reference processor
     */
    public static class ImportTransformer extends AbstractProcessor<CtImport> {
        @Override
        public boolean isToBeProcessed(CtImport candidate) {
            if (candidate == null) {
                return false;
            }
            String importStr = candidate.toString();
            return importStr.startsWith("import javax.validation");
        }
        
        @Override
        public void process(CtImport imp) {
            String oldImport = imp.toString();
            String newImport = oldImport.replace("import javax.validation", "import jakarta.validation");
            
            // We can't easily replace imports, but type reference transformation
            // should cause auto-imports to generate correct imports
            System.out.println("Need to transform import: " + oldImport + " -> " + newImport);
            // Import transformation will happen automatically when type references are updated
        }
    }
}