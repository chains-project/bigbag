package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A configurable Spoon transformation that replaces multiple fully-qualified type names.
 * This can be used to fix multiple breaking API changes in a single pass.
 */
public class ConfigurableTypeReplacer {
    
    private final Launcher launcher;
    private final List<TypeReplacement> replacements;
    
    public static class TypeReplacement {
        private final String oldTypeName;
        private final String newTypeName;
        
        public TypeReplacement(String oldTypeName, String newTypeName) {
            this.oldTypeName = oldTypeName;
            this.newTypeName = newTypeName;
        }
        
        public String getOldTypeName() { return oldTypeName; }
        public String getNewTypeName() { return newTypeName; }
    }
    
    public ConfigurableTypeReplacer(Launcher launcher, List<TypeReplacement> replacements) {
        this.launcher = launcher;
        this.replacements = new ArrayList<>(replacements);
    }
    
    public ConfigurableTypeReplacer(Launcher launcher, TypeReplacement... replacements) {
        this(launcher, Arrays.asList(replacements));
    }
    
    public void transform(CtModel model) {
        System.out.println("Applying " + replacements.size() + " type replacement(s)");
        
        for (TypeReplacement replacement : replacements) {
            System.out.println("  " + replacement.getOldTypeName() + " -> " + replacement.getNewTypeName());
        }
        
        // Replace type references
        replaceTypeReferences(model);
        
        // Replace imports (more carefully to avoid duplicates)
        replaceImports(model);
    }
    
    private void replaceTypeReferences(CtModel model) {
        for (TypeReplacement replacement : replacements) {
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    try {
                        return typeRef != null && replacement.getOldTypeName().equals(typeRef.getQualifiedName());
                    } catch (SpoonClassNotFoundException e) {
                        return false;
                    }
                }
            });
            
            System.out.println("Found " + typeRefs.size() + " references to " + replacement.getOldTypeName());
            
            for (CtTypeReference<?> typeRef : typeRefs) {
                CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(replacement.getNewTypeName());
                typeRef.replace(newTypeRef);
            }
        }
    }
    
    private void replaceImports(CtModel model) {
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class));
        int updatedImports = 0;
        
        for (CtCompilationUnit cu : compilationUnits) {
            List<CtImport> newImports = new ArrayList<>();
            boolean importsModified = false;
            
            for (CtImport imp : cu.getImports()) {
                if (imp.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) imp.getReference();
                    boolean replaced = false;
                    
                    for (TypeReplacement replacement : replacements) {
                        if (replacement.getOldTypeName().equals(typeRef.getQualifiedName())) {
                            // Create new import with the replacement type
                            CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(replacement.getNewTypeName());
                            CtImport newImport = launcher.getFactory().createImport(newTypeRef);
                            newImports.add(newImport);
                            replaced = true;
                            importsModified = true;
                            updatedImports++;
                            break;
                        }
                    }
                    
                    if (!replaced) {
                        // Keep the original import
                        newImports.add(imp);
                    }
                } else {
                    // Keep non-type imports
                    newImports.add(imp);
                }
            }
            
            if (importsModified) {
                // Replace all imports in the compilation unit
                cu.setImports(newImports);
            }
        }
        
        System.out.println("Updated " + updatedImports + " imports");
    }
    
    // Helper method for the specific Pub/Sub Lite breaking change
    public static ConfigurableTypeReplacer createForPubsubliteBreakingChange(Launcher launcher) {
        return new ConfigurableTypeReplacer(launcher,
            new TypeReplacement(
                "com.google.cloud.pubsublite.PublishMetadata",
                "com.google.cloud.pubsublite.MessageMetadata"
            )
            // Add more replacements here if needed
            // Example: new TypeReplacement("old.package.OldClass", "new.package.NewClass")
        );
    }
}