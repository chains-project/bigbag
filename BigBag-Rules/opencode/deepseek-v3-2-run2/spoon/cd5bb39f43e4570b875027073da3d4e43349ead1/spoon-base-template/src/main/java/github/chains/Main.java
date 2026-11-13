package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setLevel("INFO");
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Old and new type references
        String oldType = "org.codehaus.plexus.util.xml.Xpp3Dom";
        String newType = "org.apache.maven.shared.utils.xml.Xpp3Dom";
        
        System.out.println("Transforming " + oldType + " to " + newType);
        
        // Find all type references to the old type
        List<CtTypeReference<?>> oldTypeRefs = model.getElements(new Filter<CtTypeReference<?>>() {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                return typeRef != null && oldType.equals(typeRef.getQualifiedName());
            }
        });
        
        // Replace type references
        for (CtTypeReference<?> oldRef : oldTypeRefs) {
            System.out.println("Found type reference to " + oldType + " at position: " + oldRef.getPosition());
            CtTypeReference<?> newRef = launcher.getFactory().Type().createReference(newType);
            oldRef.replace(newRef);
        }
        
        // Find and update imports
        List<CtImport> imports = model.getElements(new Filter<CtImport>() {
            @Override
            public boolean matches(CtImport imp) {
                if (imp.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) imp.getReference();
                    return oldType.equals(typeRef.getQualifiedName());
                }
                return false;
            }
        });
        
        for (CtImport oldImport : imports) {
            System.out.println("Found import of " + oldType + " at position: " + oldImport.getPosition());
            CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(newType);
            CtImport newImport = launcher.getFactory().createImport(newTypeRef);
            oldImport.replace(newImport);
        }
        
        // Output transformed code
        launcher.setSourceOutputDirectory(new File(sourceDir));
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
}