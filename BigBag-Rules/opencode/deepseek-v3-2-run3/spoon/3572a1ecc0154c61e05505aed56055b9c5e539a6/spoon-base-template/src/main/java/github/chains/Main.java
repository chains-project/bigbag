package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.Query;
import spoon.reflect.factory.Factory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Generic transformation rule for Thrift 0.16.0 breaking changes.
 * This handles package relocation of transport classes from 
 * org.apache.thrift.transport to org.apache.thrift.transport.layered
 * 
 * The transformation is generic and can be applied to ANY Maven project
 * affected by the same breaking change by simply changing the input
 * source directory path.
 * 
 * Breaking Change Pattern:
 * - Old API: org.apache.thrift.transport.{TFastFramedTransport, TFramedTransport}
 * - New API: org.apache.thrift.transport.layered.{TFastFramedTransport, TFramedTransport}
 * - Transformation: Update imports and fully-qualified references
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.err.println("Example: java github.chains.Main /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying Thrift 0.16.0 breaking change transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        int transformations = applyThriftTransportTransformations(model, launcher.getFactory());
        
        System.out.println("Applied " + transformations + " transformations");
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    /**
     * Apply transformations for Thrift transport package relocation
     * @return number of transformations applied
     */
    private static int applyThriftTransportTransformations(CtModel model, Factory factory) {
        int count = 0;
        
        // Map of old fully-qualified names to new fully-qualified names
        // This is the generic pattern: any class relocated from org.apache.thrift.transport
        // to org.apache.thrift.transport.layered can be added here
        Map<String, String> relocationMap = new HashMap<>();
        relocationMap.put("org.apache.thrift.transport.TFastFramedTransport", 
                         "org.apache.thrift.transport.layered.TFastFramedTransport");
        relocationMap.put("org.apache.thrift.transport.TFramedTransport",
                         "org.apache.thrift.transport.layered.TFramedTransport");
        
        // Process all types in the model
        for (CtType<?> type : model.getAllTypes()) {
            // Get type references within this type that match our relocation map
            List<CtTypeReference<?>> typeRefs = Query.getElements(type, new TypeFilter<>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    String qualifiedName = element.getQualifiedName();
                    return relocationMap.containsKey(qualifiedName);
                }
            });
            
            // Update each matching type reference
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                String newQualifiedName = relocationMap.get(qualifiedName);
                
                // Create new type reference
                CtTypeReference<?> newTypeRef = factory.Type().createReference(newQualifiedName);
                
                // Replace the old reference
                typeRef.replace(newTypeRef);
                count++;
                
                System.out.println("Updated type reference: " + qualifiedName + " -> " + newQualifiedName + " in " + type.getQualifiedName());
            }
        }
        
        return count;
    }
}