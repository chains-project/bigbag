package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix deprecated SelectChannelConnector usage
 * in Jetty-based projects.
 * 
 * This transformation replaces:
 * - new SelectChannelConnector() with new ServerConnector(server)
 * - SelectChannelConnector.setPort(port) with ServerConnector.setPort(port)
 * - Import of org.eclipse.jetty.server.nio.SelectChannelConnector with 
 *   import of org.eclipse.jetty.server.ServerConnector
 */
public class Main {
    public static void main(String[] args) {
        // Check arguments
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/transformed");
        launcher.getEnvironment().setNoClasspath(true);
        
        // Process the code
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Replace SelectChannelConnector constructor calls
        List<CtConstructorCall> constructorCalls = 
            factory.createQuery().filterChildren(new TypeFilter<>(CtConstructorCall.class))
            .select((CtConstructorCall call) -> {
                return call.getType() != null && 
                       call.getType().getSimpleName().equals("SelectChannelConnector");
            })
            .list();
        
        for (CtConstructorCall constructorCall : constructorCalls) {
            // Replace with ServerConnector constructor
            CtTypeReference<?> serverConnectorRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
            constructorCall.setType(serverConnectorRef);
        }
        
        // Replace SelectChannelConnector.setPort calls with ServerConnector.setPort calls
        List<CtInvocation> setPortCalls = 
            factory.createQuery().filterChildren(new TypeFilter<>(CtInvocation.class))
            .select((CtInvocation invocation) -> {
                return "setPort".equals(invocation.getExecutable().getSimpleName()) &&
                       invocation.getTarget() != null &&
                       invocation.getTarget().getType() != null &&
                       invocation.getTarget().getType().getSimpleName().equals("SelectChannelConnector");
            })
            .list();
        
        for (CtInvocation setPortCall : setPortCalls) {
            // Change the target type to ServerConnector
            CtTypeReference<?> serverConnectorRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
            setPortCall.getTarget().setType(serverConnectorRef);
        }
        
        // Replace imports
        List<CtImport> imports = factory.getModel().getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null && 
                imp.getReference().getSimpleName().equals("SelectChannelConnector")) {
                // Replace the import
                CtTypeReference<?> serverConnectorRef = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
                imp.setReference(serverConnectorRef);
            }
        }
        
        System.out.println("Transformation completed for " + sourceDirectory);
    }
}