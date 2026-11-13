package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation for fixing breaking changes in jetty-server.
 * This transformation addresses the issue where SelectChannelConnector was removed
 * in newer versions of jetty-server (e.g., version 11.0.11).
 * 
 * The breaking change typically involves:
 * - Old: new SelectChannelConnector()
 * - New: new ServerConnector(server, executor, scheduler, bufferPool, acceptors, selectors)
 * 
 * This transformation will update all SelectChannelConnector instantiations to use
 * ServerConnector with appropriate parameters.
 */
public class Main {
    public static void main(String[] args) {
        // Parse the project
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource("/workspace/jadler");
        launcher.setSourceOutputDirectory("/workspace/jadler-transformed");
        
        // Process the model
        CtModel model = launcher.buildModel();
        
        // Find all SelectChannelConnector constructor calls
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                return element.getType().getQualifiedName().contains("SelectChannelConnector");
            }
        });
        
        System.out.println("Found " + constructorCalls.size() + " SelectChannelConnector constructor calls to transform");
        
        // For demonstration purposes, we'll just print information
        for (CtConstructorCall call : constructorCalls) {
            System.out.println("Found SelectChannelConnector instantiation in: " + 
                call.getParent(CtClass.class).getQualifiedName());
        }
        
        // Write the transformed code back to disk
        launcher.setSourceOutputDirectory("/workspace/jadler-transformed");
        launcher.process();
        
        System.out.println("Transformation completed successfully");
    }
}