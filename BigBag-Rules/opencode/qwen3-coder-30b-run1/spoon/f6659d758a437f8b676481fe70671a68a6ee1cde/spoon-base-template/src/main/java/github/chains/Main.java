package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base-template.jar github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/fixed");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to TrustedTagInspector
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                return element.getType().getQualifiedName().equals("org.yaml.snakeyaml.inspector.TrustedTagInspector");
            }
        });
        
        // Replace TrustedTagInspector with null (since it doesn't exist in 2.1)
        for (CtConstructorCall call : constructorCalls) {
            // Replace new TrustedTagInspector() with null
            call.replace((CtConstructorCall) null);
        }
        
        // Find and fix setTagInspector method calls
        List<CtInvocation> setTagInspectorCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setTagInspector");
            }
        });
        
        for (CtInvocation call : setTagInspectorCalls) {
            // Remove the entire setTagInspector call
            call.replace((CtInvocation) null);
        }
        
        // Generate the fixed code
        launcher.process();
        System.out.println("Transformed files generated in " + sourceDirectory + "/fixed");
    }
}