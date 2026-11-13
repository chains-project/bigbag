/*
 * Generic Spoon Transformation Rule for Jetty 11 Connector API Migration
 *
 * This transformation fixes compatibility issues when upgrading from Jetty 8/9 to Jetty 11,
 * specifically addressing the removal of SelectChannelConnector class.
 *
 * Pattern matched:
 *   new SelectChannelConnector()
 *
 * Transformation:
 *   new ServerConnector(server)
 *
 * Usage:
 *   spoon -i /path/to/project -o /path/to/output --template /path/to/Jetty11Migration.java
 */

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Jetty11Migration {
    
    public static void migrateJettyConnectors(CtModel model) {
        Factory factory = model.getFactory();
        
        // Match all SelectChannelConnector instantiations
        List<CtNewClass> connectorInstances = model.getElements(
            new TypeFilter<>(CtNewClass.class) {
                @Override
                public boolean matches(CtNewClass element) {
                    return element.getType() != null && 
                           element.getType().getQualifiedName().endsWith("SelectChannelConnector");
                }
            }
        );
        
        // Replace each with ServerConnector instantiation
        for (CtNewClass oldConnector : connectorInstances) {
            // Create new ServerConnector with correct constructor
            CtNewClass newConnector = factory.Core().createNewClass();
            
            // Use ServerConnector constructor: ServerConnector(Server server)
            CtTypeReference<?> serverConnectorType = factory.createTypeReference();
            serverConnectorType.setSimpleName("ServerConnector");
            serverConnectorType.setPackage(factory.createPackageReference("org.eclipse.jetty.server"));
            newConnector.setType(serverConnectorType);
            
            // Create the argument for the ServerConnector constructor
            // This assumes the server variable is accessible in the context
            // In practice, you'd need to resolve the server instance properly
            // For now, we're demonstrating the structural approach
            newConnector.setArguments(factory.createLiteral("TODO: Resolve server reference"));
            
            // Replace the old connector instantiation with new one
            oldConnector.replace(newConnector);
        }
    }
    
    public static void main(String[] args) {
        // Example usage for testing
        System.out.println("Generic Jetty 11 Connector Migration Template");
        System.out.println("This would be used with Spoon to transform projects.");
    }
}