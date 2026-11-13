package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        
        CtModel model = launcher.buildModel();
        
        // Find all DropwizardApacheConnector constructor calls that need fixing
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        int changes = 0;
        for (CtConstructorCall<?> call : constructorCalls) {
            // Check if this is a DropwizardApacheConnector constructor call
            if (call.getType().getQualifiedName().equals("io.dropwizard.client.DropwizardApacheConnector")) {
                // Check if it has the old signature (3 args) vs new signature (4 args)
                if (call.getArguments().size() == 3) {
                    // Add the boolean parameter to make it compatible with 4.0.0
                    call.addArgument(
                        launcher.getFactory().createCodeSnippetExpression("true")
                    );
                    changes++;
                }
            }
        }
        
        // Write the transformed files to the output directory
        launcher.process();
        System.out.println("Transformation complete. Made " + changes + " changes to files saved to " + sourceDir + "-transformed");
    }
}