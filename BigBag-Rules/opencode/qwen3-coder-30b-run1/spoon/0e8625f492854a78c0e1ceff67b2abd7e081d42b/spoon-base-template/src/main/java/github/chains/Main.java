package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Jetty 11.0.10 compatibility issues
 * This transformation addresses:
 * 1. SelectChannelConnector removal - replaces with ServerConnector
 * 2. Updates Java source/target version to 11
 */
public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler");
        launcher.setSourceOutputDirectory("/workspace/jadler-transformed");
        launcher.getEnvironment().setComplianceLevel(11);
        
        // Build the model
        CtModel model = launcher.buildModel();
        Factory factory = model.getFactory();
        
        // Find all imports of SelectChannelConnector and replace with ServerConnector
        List<CtImport> selectChannelConnectorImports = model.getElements(
            new TypeFilter<>(CtImport.class) {
                @Override
                public boolean matches(CtImport element) {
                    return element.getImportType() != null && 
                           "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(element.getImportType().getQualifiedName());
                }
            }
        );
        
        // Replace the import with ServerConnector
        for (CtImport importStmt : selectChannelConnectorImports) {
            CtTypeReference<?> newTypeRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
            importStmt.setImportType(newTypeRef);
            System.out.println("Replaced SelectChannelConnector import with ServerConnector");
        }
        
        // Find all type references to SelectChannelConnector and replace with ServerConnector
        List<CtTypeReference<?>> selectChannelConnectorRefs = model.getElements(
            new TypeFilter<>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    return element.getQualifiedName().equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
                }
            }
        );
        
        // Replace the type reference with ServerConnector
        for (CtTypeReference<?> ref : selectChannelConnectorRefs) {
            CtTypeReference<?> newTypeRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
            ref.replace(newTypeRef);
            System.out.println("Replaced SelectChannelConnector type reference with ServerConnector");
        }
        
        // Process the model and write the output
        launcher.process();
        launcher.writeProcessedSourceFiles();
        
        System.out.println("Transformation completed successfully!");
    }
}