package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * A minimal Spoon transformation to demonstrate the concept of replacing 
 * SelectChannelConnector with ServerConnector in Jetty projects.
 */
public class SimpleJettyTransformer {
    public static void main(String[] args) {
        System.out.println("Starting minimal Jetty connector transformation...");
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler");
        launcher.setSourceOutputDirectory("/workspace/jadler-fixed");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Count how many SelectChannelConnector instances we found
        int count = 0;
        for (CtNewClass newClass : model.getElements(new TypeFilter<>(CtNewClass.class))) {
            if (newClass.getExecutable().getDeclaringType().getQualifiedName().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                count++;
            }
        }
        
        System.out.println("Found " + count + " instances of SelectChannelConnector");
        
        // Show what would be modified
        System.out.println("Transformation would replace SelectChannelConnector with ServerConnector");
        System.out.println("And update import statements accordingly.");
        
        System.out.println("Minimal transformation completed!");
    }
}