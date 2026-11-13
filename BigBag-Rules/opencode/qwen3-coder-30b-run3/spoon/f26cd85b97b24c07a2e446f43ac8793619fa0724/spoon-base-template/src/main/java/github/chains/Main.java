package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

/**
 * Generic Spoon transformation to fix Jetty 11 compatibility issues.
 * Specifically addresses the removal of SelectChannelConnector in favor of ServerConnector.
 */
public class Main {
    public static void main(String[] args) {
        // Process command line arguments
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Initialize Spoon
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        CtModel model = launcher.buildModel();
        
        // Transformations
        transformSelectChannelConnector(model, launcher.getFactory());
        
        // Process and write files
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Transforms uses of SelectChannelConnector to ServerConnector
     */
    private static void transformSelectChannelConnector(CtModel model, Factory factory) {
        // Find all constructor calls to SelectChannelConnector
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall constructorCall : constructorCalls) {
            CtTypeReference<?> typeRef = constructorCall.getType();
            
            if (typeRef != null && 
                typeRef.getQualifiedName().contains("SelectChannelConnector")) {
                // Replace the constructor call with ServerConnector
                CtTypeReference<?> newTypeRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
                constructorCall.setType(newTypeRef);
            }
        }
        
        // Also replace import statements
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null && 
                imp.getReference().getQualifiedName().contains("SelectChannelConnector")) {
                
                // Create new import for ServerConnector
                CtTypeReference<?> newImportRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
                imp.setReference(newImportRef);
            }
        }
    }
}
        
        String sourceDirectory = args[0];
        
        // Initialize Spoon
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        CtModel model = launcher.buildModel();
        
        // Transformations
        transformSelectChannelConnector(model, launcher.getFactory());
        
        // Process and write files
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Transforms uses of SelectChannelConnector to ServerConnector
     */
    private static void transformSelectChannelConnector(CtModel model, Factory factory) {
        // Find all constructor calls to SelectChannelConnector
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall constructorCall : constructorCalls) {
            CtTypeReference<?> typeRef = constructorCall.getType();
            
            if (typeRef != null && 
                typeRef.getQualifiedName().contains("SelectChannelConnector")) {
                // Replace the constructor call with ServerConnector
                CtTypeReference<?> newTypeRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
                constructorCall.setType(newTypeRef);
            }
        }
        
        // Also replace import statements
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null && 
                imp.getReference().getQualifiedName().contains("SelectChannelConnector")) {
                
                // Create new import for ServerConnector
                CtTypeReference<?> newImportRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
                imp.setReference(newImportRef);
            }
        }
    }
}