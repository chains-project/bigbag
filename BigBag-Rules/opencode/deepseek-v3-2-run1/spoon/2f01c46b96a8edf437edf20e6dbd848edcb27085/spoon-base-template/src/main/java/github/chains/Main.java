package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDirectory> <oldFullyQualifiedType> <newFullyQualifiedType>");
            System.err.println("Example: java Main /path/to/src org.codehaus.plexus.util.xml.Xpp3Dom org.codehaus.plexus.configuration.PlexusConfiguration");
            System.err.println("\nThis transformation replaces:");
            System.err.println("1. Imports of the old type");
            System.err.println("2. Type references in code (variable declarations, method parameters, etc.)");
            System.err.println("3. Cast expressions");
            System.err.println("\nNote: Method invocations and references require additional handling.");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String oldType = args[1];
        String newType = args.length > 2 ? args[2] : null;
        
        if (newType == null) {
            System.err.println("Error: New type not specified. Please provide replacement type.");
            System.exit(1);
        }
        
        System.out.println("Creating Spoon transformation for: " + oldType + " -> " + newType);
        System.out.println("Source directory: " + sourceDirectory);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            CtModel model = launcher.buildModel();
            
            int transformations = 0;
            
            // Find and replace imports
            List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
            for (CtImport ctImport : imports) {
                if (ctImport.getReference() != null) {
                    String importedType = ctImport.getReference().toString();
                    if (importedType.equals(oldType)) {
                        System.out.println("Replacing import: " + oldType + " -> " + newType);
                        CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newType);
                        ctImport.setReference(newTypeRef);
                        transformations++;
                    }
                }
            }
            
            // Find and replace type references in code
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference<?> typeRef : typeRefs) {
                if (typeRef.getQualifiedName().equals(oldType)) {
                    System.out.println("Replacing type reference: " + oldType + " -> " + newType + " at position " + typeRef.getPosition());
                    CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newType);
                    typeRef.replace(newTypeRef);
                    transformations++;
                }
            }
            
            // Save transformed code
            if (transformations > 0) {
                System.out.println("\nApplied " + transformations + " transformations.");
                launcher.setSourceOutputDirectory("./transformed-output");
                launcher.prettyprint();
                System.out.println("Transformation complete! Output saved to ./transformed-output");
                System.out.println("\nIMPORTANT: Check for method calls that may need updating:");
                System.out.println("- Method references (e.g., Xpp3Dom::getChildren)");
                System.out.println("- Method invocations (e.g., xpp3Dom.getValue())");
                System.out.println("- Constructor calls (e.g., new Xpp3Dom())");
            } else {
                System.out.println("No transformations applied. No instances of " + oldType + " found.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}