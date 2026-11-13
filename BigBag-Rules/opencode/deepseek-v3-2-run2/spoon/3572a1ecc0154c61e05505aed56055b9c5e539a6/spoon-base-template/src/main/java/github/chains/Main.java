package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtJavaDocTag;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;
import spoon.support.visitor.SignaturePrinter;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class Main {
    
    // Configuration: Map of old package patterns to new package locations
    private static final Map<String, String> TRANSPORT_CLASS_MIGRATIONS = new HashMap<>();
    
    static {
        // Map old fully-qualified class names to new ones
        TRANSPORT_CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFastFramedTransport",
            "org.apache.thrift.transport.layered.TFastFramedTransport"
        );
        TRANSPORT_CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFramedTransport",
            "org.apache.thrift.transport.layered.TFramedTransport"
        );
        // Also handle the Factory inner classes
        TRANSPORT_CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFastFramedTransport$Factory",
            "org.apache.thrift.transport.layered.TFastFramedTransport$Factory"
        );
        TRANSPORT_CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFramedTransport$Factory",
            "org.apache.thrift.transport.layered.TFramedTransport$Factory"
        );
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.addInputResource(sourceDirectory);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Count transformations
            int importTransformations = 0;
            int typeReferenceTransformations = 0;
            
            // Get the factory from launcher
            spoon.reflect.factory.Factory factory = launcher.getFactory();
            
            // Process all imports in the model
            List<CtImport> imports = Query.getElements(factory, new TypeFilter<>(CtImport.class));
            for (CtImport imp : imports) {
                String importString = imp.getReference().toString();
                
                // Check if this import needs to be updated
                for (Map.Entry<String, String> migration : TRANSPORT_CLASS_MIGRATIONS.entrySet()) {
                    String oldClass = migration.getKey();
                    String newClass = migration.getValue();
                    
                    if (importString.equals(oldClass) || 
                        importString.startsWith(oldClass + ".")) {
                        
                        // Create new import reference
                        CtTypeReference<?> newRef = factory.Type().createReference(newClass);
                        imp.setReference(newRef);
                        importTransformations++;
                        System.out.println("Updated import: " + oldClass + " -> " + newClass);
                        break;
                    }
                }
            }
            
            // Process all type references in the code
            List<CtTypeReference<?>> typeRefs = Query.getElements(factory, new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference<?> typeRef : typeRefs) {
                String typeName = typeRef.getQualifiedName();
                
                // Check if this type reference needs to be updated
                for (Map.Entry<String, String> migration : TRANSPORT_CLASS_MIGRATIONS.entrySet()) {
                    String oldClass = migration.getKey();
                    String newClass = migration.getValue();
                    
                    if (typeName.equals(oldClass)) {
                        // Update the type reference
                        CtTypeReference<?> newRef = factory.Type().createReference(newClass);
                        typeRef.replace(newRef);
                        typeReferenceTransformations++;
                        System.out.println("Updated type reference: " + oldClass + " -> " + newClass);
                        break;
                    }
                }
            }
            
            // Process fully qualified names in the code (for cases where class is used without import)
            // We need to search for occurrences in the AST
            List<CtType<?>> allTypes = Query.getElements(factory, new TypeFilter<>(CtType.class));
            for (CtType<?> type : allTypes) {
                String sourceCode = type.toString();
                
                // Simple string replacement for fully qualified names in the source
                // This is a fallback for cases not caught by the import/type reference transformations
                for (Map.Entry<String, String> migration : TRANSPORT_CLASS_MIGRATIONS.entrySet()) {
                    String oldClass = migration.getKey();
                    String newClass = migration.getValue();
                    
                    if (sourceCode.contains(oldClass)) {
                        // Note: In a more sophisticated implementation, we would use
                        // Spoon's AST rewriting capabilities instead of string replacement
                        // For simplicity, we're showing the pattern here
                        System.out.println("Found reference to " + oldClass + " in " + type.getQualifiedName());
                    }
                }
            }
            
            // Apply all transformations
            System.out.println("\nTransformation Summary:");
            System.out.println("Import statements updated: " + importTransformations);
            System.out.println("Type references updated: " + typeReferenceTransformations);
            System.out.println("Total transformations: " + (importTransformations + typeReferenceTransformations));
            
            if (importTransformations + typeReferenceTransformations > 0) {
                // Output the transformed code
                launcher.setSourceOutputDirectory("./transformed-output");
                launcher.prettyprint();
                System.out.println("\nTransformed code written to: ./transformed-output");
            } else {
                System.out.println("\nNo transformations were needed.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Helper method to check if a class is one of the transport classes that moved.
     */
    private static boolean isTransportClassMigration(String className) {
        return TRANSPORT_CLASS_MIGRATIONS.containsKey(className) ||
               className.startsWith("org.apache.thrift.transport.TFastFramedTransport") ||
               className.startsWith("org.apache.thrift.transport.TFramedTransport");
    }
}