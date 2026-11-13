package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation to fix breaking changes in plexus-utils dependency.
 * Specifically addresses removal of org.codehaus.plexus.util.xml.Xpp3Dom class in version 4.0.0.
 * This transformation removes Xpp3Dom imports to maintain compatibility.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setAutoImports(true);

        // Build the model
        launcher.buildModel();

        // Remove imports to the problematic Xpp3Dom class
        launcher.getFactory().getModel().getRootPackage()
                .filterChildren(new TypeFilter<>(CtImport.class))
                .forEach(importDecl -> {
                    if (importDecl.getReference() != null && 
                            importDecl.getReference().getQualifiedName().equals("org.codehaus.plexus.util.xml.Xpp3Dom")) {
                        importDecl.delete();
                    }
                });

        // Write processed files to output directory
        launcher.setSourceOutputDirectory("transformed-output");
        launcher.process();
        
        System.out.println("Transformation completed for " + sourceDirectory);
    }
}