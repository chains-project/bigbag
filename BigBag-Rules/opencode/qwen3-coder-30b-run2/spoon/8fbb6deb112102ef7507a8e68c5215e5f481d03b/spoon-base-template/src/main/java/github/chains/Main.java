package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Jetty 11 breaking changes:
 * Replaces SelectChannelConnector with ServerConnector
 */
public class Main {
    public static void main(String[] args) {
        // Parse the source code
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler"); // Source directory
        launcher.setSourceOutputDirectory("/workspace/jadler-fixed"); // Output directory
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to SelectChannelConnector
        List<CtConstructorCall<?>> constructorCalls = model.getElements(
                new TypeFilter<>(CtConstructorCall.class)
        );
        
        boolean foundChanges = false;
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            // Look for constructor calls with SelectChannelConnector
            if (constructorCall.getType().getSimpleName().equals("SelectChannelConnector")) {
                // Replace it with ServerConnector
                constructorCall.setType(
                    launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector")
                );
                foundChanges = true;
            }
        }
        
        if (foundChanges) {
            // Write the modified code back to disk
            launcher.prettyprint();
            System.out.println("Transformation complete! Updated SelectChannelConnector to ServerConnector.");
        } else {
            System.out.println("No SelectChannelConnector references found - no changes needed.");
        }
    }
}