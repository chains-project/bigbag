package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation to fix Xpp3Dom usage in Maven projects.
 * This rule addresses the breaking change where Xpp3Dom was removed from plexus-utils 4.0.0.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon factory and process the code
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-fixed");
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Apply the transformation
        fixXpp3DomUsage(factory);
        
        // Print the result
        System.out.println("Xpp3Dom fixes applied to " + sourceDirectory);
        
        // Save the modified model
        launcher.setSourceOutputDirectory(sourceDirectory + "-fixed");
        launcher.process();
    }
    
    public static void fixXpp3DomUsage(Factory factory) {
        // Remove imports of Xpp3Dom
        List<CtImport> importsToRemove = factory.getModel().getElements(new TypeFilter<>(CtImport.class))
            .stream()
            .filter(importDecl -> {
                try {
                    return importDecl.getReference() != null && 
                        importDecl.getReference().toString().contains("Xpp3Dom");
                } catch (Exception e) {
                    return false;
                }
            })
            .collect(Collectors.toList());
            
        for (CtImport importDecl : importsToRemove) {
            importDecl.delete();
        }
        
        System.out.println("Applied basic Xpp3Dom fixes");
    }
}