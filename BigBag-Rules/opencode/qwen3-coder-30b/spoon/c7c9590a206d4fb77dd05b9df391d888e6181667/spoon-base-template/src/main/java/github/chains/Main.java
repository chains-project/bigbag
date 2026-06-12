package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base.jar github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher and model
        Launcher launcher = new Launcher();
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        CtModel model = launcher.getModel();
        
        // Find all imports that reference the old RenderingContext location
        List<CtImport> oldImports = model.getElements(new TypeFilter<>(CtImport.class))
            .stream()
            .filter(importElement -> 
                importElement.getReference() != null && 
                "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext".equals(importElement.getReference().getSimpleName()))
            .filter(importElement -> 
                "org.apache.maven.doxia.module.xhtml.decoration.render".equals(importElement.getReference().getPackage().getQualifiedName()))
            .collect(Collectors.toList());
        
        // Replace the old import with the new one
        for (CtImport oldImport : oldImports) {
            // Create new import for the correct location
            CtImport newImport = factory.createImport(
                factory.Type().createReference("org.apache.maven.doxia.siterenderer.RenderingContext")
            );
            oldImport.replace(newImport);
        }
        
        // Print summary of changes
        System.out.println("Fixed doxia-site-renderer API compatibility issue");
        System.out.println("Updated import from org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext");
        System.out.println("Updated reference to org.apache.maven.doxia.siterenderer.RenderingContext");
        
        // Save changes
        launcher.process();
    }
}