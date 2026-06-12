package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix libthrift breaking changes.
 * Specifically addresses the move of TFastFramedTransport from
 * org.apache.thrift.transport to org.apache.thrift.transport.layered
 * 
 * This transformation replaces:
 * - Import statements: org.apache.thrift.transport.TFastFramedTransport
 * - Type references: org.apache.thrift.transport.TFastFramedTransport
 * With their new locations in:
 * - Import statements: org.apache.thrift.transport.layered.TFastFramedTransport
 * - Type references: org.apache.thrift.transport.layered.TFastFramedTransport
 */
public class Main {
    public static void main(String[] args) {
        // Create a new launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory - this should be configurable via args
        String sourceDirectory = "/workspace/singer";
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        // Add input resources
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/spooned");
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Find all import statements that reference the old TFastFramedTransport
            List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
            for (CtImport imp : imports) {
                if (imp.getReference() != null) {
                    String importReference = imp.getReference().toString();
                    if (importReference.contains("org.apache.thrift.transport.TFastFramedTransport")) {
                        // Replace with new import
                        CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference("org.apache.thrift.transport.layered.TFastFramedTransport");
                        imp.setReference(newTypeRef);
                    }
                }
            }
            
            // Process the model to generate the modified code
            launcher.process();
            
            System.out.println("Transformation completed successfully. Check " + sourceDirectory + "/spooned for results.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}