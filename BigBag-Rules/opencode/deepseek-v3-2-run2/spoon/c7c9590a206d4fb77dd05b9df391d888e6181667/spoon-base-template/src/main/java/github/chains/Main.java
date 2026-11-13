package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Main {
    
    // Configuration parameters - these should be externalized for reuse
    private static final String OLD_RENDERING_CONTEXT_PACKAGE = "org.apache.maven.doxia.module.xhtml.decoration.render";
    private static final String NEW_RENDERING_CONTEXT_PACKAGE = "org.apache.maven.doxia.siterenderer";
    private static final String RENDERING_CONTEXT_CLASS_NAME = "RenderingContext";
    private static final String OLD_RENDERING_CONTEXT_FQN = OLD_RENDERING_CONTEXT_PACKAGE + "." + RENDERING_CONTEXT_CLASS_NAME;
    private static final String NEW_RENDERING_CONTEXT_FQN = NEW_RENDERING_CONTEXT_PACKAGE + "." + RENDERING_CONTEXT_CLASS_NAME;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying transformation to: " + sourceDirectory);
        
        try {
            applyTransformation(sourceDirectory);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void applyTransformation(String sourceDirectory) {
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Configure launcher
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(true); // Use no classpath to avoid dependency issues
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation
        transformRenderingContextReferences(model);
        
        // Write transformed code back to disk
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.prettyprint();
    }
    
    private static void transformRenderingContextReferences(CtModel model) {
        // Find all type references to the old RenderingContext class
        List<CtTypeReference<?>> oldTypeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                return isOldRenderingContextTypeReference(typeRef);
            }
        });
        
        System.out.println("Found " + oldTypeRefs.size() + " type references to old RenderingContext");
        
        // Replace each old type reference with new one
        int replacements = 0;
        for (CtTypeReference<?> oldTypeRef : oldTypeRefs) {
            try {
                // Create new type reference
                CtTypeReference<?> newTypeRef = oldTypeRef.getFactory().Type().createReference(NEW_RENDERING_CONTEXT_FQN);
                
                // Copy type arguments if present
                if (!oldTypeRef.getActualTypeArguments().isEmpty()) {
                    newTypeRef.setActualTypeArguments(oldTypeRef.getActualTypeArguments());
                }
                
                // Replace the type reference
                oldTypeRef.replace(newTypeRef);
                replacements++;
                
                if (oldTypeRef.getPosition() != null && oldTypeRef.getPosition().getFile() != null) {
                    System.out.println("Replaced type reference at: " + oldTypeRef.getPosition().getFile().getName() + ":" + oldTypeRef.getPosition().getLine());
                }
            } catch (Exception e) {
                System.err.println("Error replacing type reference: " + e.getMessage());
            }
        }
        
        System.out.println("Replaced " + replacements + " type references");
        
        // Note: Spoon's auto-import feature should handle import updates automatically
        // when we replace type references. The old imports may remain but won't be used.
    }
    
    private static boolean isOldRenderingContextTypeReference(CtTypeReference<?> typeRef) {
        try {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null) {
                return qualifiedName.equals(OLD_RENDERING_CONTEXT_FQN) ||
                       qualifiedName.startsWith(OLD_RENDERING_CONTEXT_PACKAGE + ".");
            }
            
            // Check simple name
            String simpleName = typeRef.getSimpleName();
            if (RENDERING_CONTEXT_CLASS_NAME.equals(simpleName)) {
                // Might be imported from old package
                // We'll replace it and let Spoon's auto-import handle it
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}