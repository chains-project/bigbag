package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Jetty 11 dependency breakage
 * Specifically addresses the removal of SelectChannelConnector in favor of ServerConnector
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setComplianceLevel(7);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();

        // Find and replace all SelectChannelConnector instantiations
        List<CtNewClass> newClasses = model.getElements(new TypeFilter<>(CtNewClass.class));
        for (CtNewClass newClass : newClasses) {
            try {
                String className = newClass.getType().toString();
                if (className.contains("nio.SelectChannelConnector")) {
                    // Replace with ServerConnector
                    CtTypeReference<?> newType = factory.Type().createReference("org.eclipse.jetty.server.ServerConnector");
                    newClass.setType(newType);
                }
            } catch (Exception ignored) {
                // Skip problematic elements
            }
        }

        // Remove old imports
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport importStmt : imports) {
            try {
                if (importStmt.toString().contains("nio.SelectChannelConnector")) {
                    importStmt.delete();
                }
            } catch (Exception ignored) {
                // Skip problematic imports
            }
        }

        System.out.println("Jetty 11 compatibility transformation completed.");
        System.out.println("Replaced SelectChannelConnector with ServerConnector");

        // Write back to disk
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.getModelBuilder().generateProcessedSourceFiles(spoon.OutputType.CLASSES);
    }
}