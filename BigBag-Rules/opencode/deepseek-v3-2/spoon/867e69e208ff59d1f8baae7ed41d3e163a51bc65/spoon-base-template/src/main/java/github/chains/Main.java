package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation for libthrift 0.16.0 breaking changes.
 * 
 * This transformation handles the migration of TFramedTransport and TFastFramedTransport
 * from org.apache.thrift.transport to org.apache.thrift.transport.layered
 * in libthrift 0.16.0.
 * 
 * Usage: java -jar spoon-transformation.jar <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Applies libthrift 0.16.0 migration transformations");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying libthrift 0.16.0 migration transformations to: " + sourceDir);
        System.out.println("Transforming TFramedTransport and TFastFramedTransport imports...");
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true); // Don't need full classpath for type reference replacement
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setLevel("OFF"); // Reduce verbose output
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation
        int changes = transformThriftTransportClasses(model);
        
        if (changes > 0) {
            // Output transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Updated " + changes + " type references.");
            System.out.println("\nNOTE: Additional manual fixes may be needed for:");
            System.out.println("1. Constructor signatures that now throw TTransportException");
            System.out.println("2. Any other API changes in libthrift 0.16.0");
        } else {
            System.out.println("No transformations applied - no matching type references found.");
        }
    }
    
    private static int transformThriftTransportClasses(CtModel model) {
        // Define the mapping of old to new types
        List<TypeMapping> typeMappings = new ArrayList<>();
        typeMappings.add(new TypeMapping(
            "org.apache.thrift.transport.TFramedTransport",
            "org.apache.thrift.transport.layered.TFramedTransport"
        ));
        typeMappings.add(new TypeMapping(
            "org.apache.thrift.transport.TFastFramedTransport", 
            "org.apache.thrift.transport.layered.TFastFramedTransport"
        ));
        
        final int[] changeCount = {0};
        
        // Get all types in the model
        List<CtType<?>> allTypes = model.getAllTypes().stream().toList();
        
        for (CtType<?> type : allTypes) {
            // Create a scanner to visit all elements
            CtScanner scanner = new CtScanner() {
                @Override
                public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
                    super.visitCtTypeReference(reference);
                    
                    // Check if this reference matches any old type
                    for (TypeMapping mapping : typeMappings) {
                        String qualifiedName = reference.getQualifiedName();
                        if (qualifiedName != null && qualifiedName.equals(mapping.oldType)) {
                            // Replace with new type reference
                            CtTypeReference<T> newRef = reference.getFactory()
                                .createReference(mapping.newType);
                            reference.replace(newRef);
                            changeCount[0]++;
                            System.out.println("  - Replaced " + mapping.oldType + " with " + mapping.newType);
                        }
                    }
                }
            };
            
            // Apply the scanner to this type
            scanner.scan(type);
        }
        
        return changeCount[0];
    }
    
    /**
     * Simple data class for type mapping.
     */
    private static class TypeMapping {
        final String oldType;
        final String newType;
        
        TypeMapping(String oldType, String newType) {
            this.oldType = oldType;
            this.newType = newType;
        }
    }
}