package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.Arrays;

/**
 * Generic Spoon transformation to fix deprecated SelectChannelConnector usage
 * in Jetty 11.0.14 projects.
 * 
 * This transformation replaces:
 *   new SelectChannelConnector()
 * with:
 *   new AbstractNetworkConnector(server, null, null, null, 0)
 * 
 * This handles the breaking change from Jetty 8 to 11.0.14 where 
 * SelectChannelConnector was removed.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.buildModel();
        
        // Collect elements to replace for processing
        boolean changed = false;
        
        // Find all instantiations of SelectChannelConnector
        for (CtNewClass newClass : launcher.getModel().getElements(new TypeFilter<>(CtNewClass.class))) {
            CtTypeReference<?> typeRef = newClass.getType();
            if (typeRef != null && 
                "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(typeRef.getQualifiedName())) {
                
                Factory factory = newClass.getFactory();
                
                // Create replacement constructor call with new API parameters
                // AbstractNetworkConnector(Server server, Executor executor, Scheduler scheduler, 
                // ByteBufferPool bufferPool, int port, ConnectionFactory... connectionFactories)
                CtConstructorCall<?> replacement = factory.Code().createConstructorCall(
                    factory.Type().createReference("org.eclipse.jetty.server.AbstractNetworkConnector"),
                    // Parameters: server, executor, scheduler, bufferPool, port
                    factory.Code().createLiteral(null), // server  
                    factory.Code().createLiteral(null), // executor
                    factory.Code().createLiteral(null), // scheduler
                    factory.Code().createLiteral(null), // bufferPool
                    factory.Code().createLiteral(0)     // port
                );
                
                // Replace the element
                newClass.replace(replacement);
                changed = true;
            }
        }
        
        if (changed) {
            // Export modified source back to the original directory
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.process();
            System.out.println("Successfully transformed SelectChannelConnector to AbstractNetworkConnector");
        } else {
            System.out.println("No SelectChannelConnector instances found to transform");
        }
    }
}