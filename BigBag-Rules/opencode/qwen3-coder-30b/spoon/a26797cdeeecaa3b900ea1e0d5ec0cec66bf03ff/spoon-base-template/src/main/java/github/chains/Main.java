package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtImport;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar spoon-transformer.jar <path-to-source>");
            return;
        }
        
        String sourcePath = args[0];
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        launcher.setSourceOutputDirectory("transformed-src");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Process all types in the model
        for (CtType<?> type : model.getAllTypes()) {
            Factory factory = type.getFactory();
            
            // Remove imports of the removed interface
            List<CtImport> imports = factory.CompilationUnit().getDeclaredImports();
            for (CtImport imp : imports) {
                if (imp.getReference() != null && 
                    imp.getReference().getQualifiedName().equals("org.slf4j.spi.LoggingEventAware")) {
                    imp.delete();
                }
            }
            
            // Remove any references to the interface in implements clauses
            // This is a simplified approach - we'll just remove all references to the interface
            type.filterChildren(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    if ("org.slf4j.spi.LoggingEventAware".equals(element.getQualifiedName())) {
                        element.delete();
                        return true;
                    }
                    return false;
                }
            });
        }
        
        // Generate the transformed code
        launcher.setSourceOutputDirectory("transformed-src");
        launcher.process();
        
        System.out.println("Transformation complete. Check the 'transformed-src' directory.");
    }
}