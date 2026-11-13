package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix libthrift 0.16.0 breaking change in TSerializer constructor calls.
 * 
 * Fixes: TSerializer() default constructor now throws TTransportException in libthrift 0.16.0+
 * Change: Replace new TSerializer() with new TSerializer(new TBinaryProtocol.Factory())
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.buildModel();
        
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();
        
        // Find all TSerializer constructor calls
        List<CtConstructorCall<?>> constructorCalls = 
            model.getElements(new TypeFilter<>(CtConstructorCall.class));
            
        for (CtConstructorCall<?> call : constructorCalls) {
            // Check if it's a constructor call to TSerializer with no arguments
            if (call.getExecutable() != null && 
                call.getExecutable().getDeclaringType() != null &&
                call.getExecutable().getDeclaringType().getQualifiedName().equals("org.apache.thrift.TSerializer") &&
                call.getArguments().isEmpty()) {
                
                // Create new constructor call that passes TBinaryProtocol.Factory to TSerializer
                // We'll do this by getting the existing constructor call text and modifying it
                call.replace(factory.Code().createCodeSnippetStatement(
                    "new org.apache.thrift.TSerializer(new org.apache.thrift.protocol.TBinaryProtocol.Factory())"
                ));
            }
        }
        
        // Save modified files
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
}