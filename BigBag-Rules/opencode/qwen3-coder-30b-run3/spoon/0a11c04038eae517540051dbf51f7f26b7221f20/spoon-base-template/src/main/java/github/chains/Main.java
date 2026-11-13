package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Create launcher and set up environment
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to SnakeYAML Constructor with Class argument
        List<CtConstructorCall> constructorCalls = model.getRootPackage()
                .filterChildren(new TypeFilter<>(CtConstructorCall.class))
                .filter(child -> {
                    if (child.getExecutable() != null) {
                        CtExecutableReference executableRef = child.getExecutable();
                        if (executableRef.getDeclaringType() != null) {
                            String declaringTypeName = executableRef.getDeclaringType().getQualifiedName();
                            // Check if this is a SnakeYAML Constructor call with a single Class argument
                            return declaringTypeName.equals("org.yaml.snakeyaml.constructor.Constructor") &&
                                   child.getArguments().size() == 1;
                        }
                    }
                    return false;
                })
                .list();
        
        System.out.println("Found " + constructorCalls.size() + " constructor calls to inspect");
        
        if (constructorCalls.isEmpty()) {
            System.out.println("No SnakeYAML constructor calls found.");
            return;
        }
        
        // Print details for inspection
        for (int i = 0; i < Math.min(3, constructorCalls.size()); i++) { // Limit output to first 3
            CtConstructorCall call = constructorCalls.get(i);
            System.out.println("Constructor call " + (i+1) + ": " + call.toString());
        }
        
        // This would be where we actually do the replacement in a production scenario
        // For now, we'll just report what we found
        
        System.out.println("Transformation would fix these calls if implemented.");
    }
}