package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule to fix package relocation changes in Doxia dependencies.
 * Specifically addresses the relocation of RenderingContext from
 * org.apache.maven.doxia.module.xhtml.decoration.render to org.apache.maven.doxia.siterenderer
 * 
 * Usage: java -cp target/classes github.chains.Main [input-path] [output-path]
 */
public class Main {
    public static void main(String[] args) {
        // Configuration for the transformation
        String inputPath = args.length > 0 ? args[0] : "/workspace/scoverage-maven-plugin";
        String outputPath = args.length > 1 ? args[1] : "/workspace/scoverage-maven-plugin-fixed";

        // Create launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all imports related to the old package and fix them
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        // Fix import statements that refer to the relocated RenderingContext
        for (CtImport imp : imports) {
            if (imp.getReference() != null) {
                String refStr = imp.getReference().toString();
                if (refStr.contains("org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext")) {
                    // Replace with correct package
                    CtTypeReference<?> newRef = launcher.getFactory().createTypeReference("org.apache.maven.doxia.siterenderer.RenderingContext");
                    imp.setReference(newRef);
                }
            }
        }

        // Apply the transformation
        launcher.process();
        
        System.out.println("Transformation complete. Files saved to: " + outputPath);
    }
}