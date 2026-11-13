package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformer.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Transforming source directory: " + sourceDirectory);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDirectory);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Count transformations
        int transformCount = 0;
        
        // Find and fix type references in code
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifedName = typeRef.getQualifiedName();
            if ("org.codehaus.plexus.util.xml.Xpp3Dom".equals(qualifedName)) {
                System.out.println("Found type reference: " + qualifedName + " at " + typeRef.getPosition());
                
                // Replace the type reference
                CtTypeReference<?> newTypeRef = launcher.getFactory().Type()
                    .createReference("org.apache.maven.shared.utils.xml.Xpp3Dom");
                typeRef.replace(newTypeRef);
                
                transformCount++;
                System.out.println("  Replaced type reference");
            }
        }
        
        // Output transformed code
        if (transformCount > 0) {
            System.out.println("\nPerforming " + transformCount + " transformations...");
            
            // Create output directory (same as input with -transformed suffix)
            File outputDir = new File(sourceDirectory + "-transformed");
            outputDir.mkdirs();
            
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("Transformation complete. Output written to: " + outputDir.getAbsolutePath());
            
            // Also print summary of files that were modified
            System.out.println("\nSummary of changes:");
            System.out.println("- Changed type references from 'org.codehaus.plexus.util.xml.Xpp3Dom'");
            System.out.println("  to 'org.apache.maven.shared.utils.xml.Xpp3Dom'");
            System.out.println("\nNote: You may need to:");
            System.out.println("1. Update imports manually in transformed files");
            System.out.println("2. Add maven-shared-utils dependency:");
            System.out.println("<dependency>");
            System.out.println("  <groupId>org.apache.maven.shared</groupId>");
            System.out.println("  <artifactId>maven-shared-utils</artifactId>");
            System.out.println("  <version>3.3.4</version>");
            System.out.println("</dependency>");
        } else {
            System.out.println("No Xpp3Dom imports or references found to transform.");
        }
    }
}