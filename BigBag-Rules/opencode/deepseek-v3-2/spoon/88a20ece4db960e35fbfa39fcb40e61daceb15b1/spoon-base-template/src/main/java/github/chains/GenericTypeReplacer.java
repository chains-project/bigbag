package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;
import java.util.List;

/**
 * A generic Spoon transformation that replaces one fully-qualified type name with another.
 * This can be used to fix breaking API changes where a type has been renamed or moved.
 */
public class GenericTypeReplacer {
    
    private final Launcher launcher;
    private final String oldTypeName;
    private final String newTypeName;
    
    public GenericTypeReplacer(Launcher launcher, String oldTypeName, String newTypeName) {
        this.launcher = launcher;
        this.oldTypeName = oldTypeName;
        this.newTypeName = newTypeName;
    }
    
    public void transform(CtModel model) {
        System.out.println("Applying transformation: " + oldTypeName + " -> " + newTypeName);
        
        // Replace type references
        replaceTypeReferences(model);
        
        // Replace imports
        replaceImports(model);
    }
    
    private void replaceTypeReferences(CtModel model) {
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                try {
                    return typeRef != null && oldTypeName.equals(typeRef.getQualifiedName());
                } catch (SpoonClassNotFoundException e) {
                    return false;
                }
            }
        });
        
        System.out.println("Found " + typeRefs.size() + " references to " + oldTypeName);
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newTypeName);
            typeRef.replace(newTypeRef);
        }
    }
    
    private void replaceImports(CtModel model) {
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class));
        int importCount = 0;
        
        for (CtCompilationUnit cu : compilationUnits) {
            for (CtImport imp : cu.getImports()) {
                if (imp.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) imp.getReference();
                    if (oldTypeName.equals(typeRef.getQualifiedName())) {
                        CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newTypeName);
                        imp.setReference(newTypeRef);
                        importCount++;
                    }
                }
            }
        }
        
        System.out.println("Updated " + importCount + " imports of " + oldTypeName);
    }
}