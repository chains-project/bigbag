package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class SimpleTest {
    
    private static final Map<String, String> TRANSPORT_CLASS_MIGRATIONS = new HashMap<>();
    
    static {
        TRANSPORT_CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFastFramedTransport",
            "org.apache.thrift.transport.layered.TFastFramedTransport"
        );
        TRANSPORT_CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFramedTransport",
            "org.apache.thrift.transport.layered.TFramedTransport"
        );
        TRANSPORT_CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFastFramedTransport$Factory",
            "org.apache.thrift.transport.layered.TFastFramedTransport$Factory"
        );
        TRANSPORT_CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFramedTransport$Factory",
            "org.apache.thrift.transport.layered.TFramedTransport$Factory"
        );
    }
    
    public static void main(String[] args) throws Exception {
        String sourceDirectory = "/workspace/test-transformation/src";
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Get the factory
        spoon.reflect.factory.Factory factory = launcher.getFactory();
        
        System.out.println("Processing imports...");
        
        // Process all imports
        List<CtImport> imports = Query.getElements(factory, new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            String importString = imp.getReference().toString();
            System.out.println("Found import: " + importString);
            
            for (Map.Entry<String, String> migration : TRANSPORT_CLASS_MIGRATIONS.entrySet()) {
                String oldClass = migration.getKey();
                String newClass = migration.getValue();
                
                if (importString.equals(oldClass)) {
                    System.out.println("  -> Needs update: " + oldClass + " -> " + newClass);
                    CtTypeReference<?> newRef = factory.Type().createReference(newClass);
                    imp.setReference(newRef);
                    break;
                }
            }
        }
        
        // Process all type references
        System.out.println("\nProcessing type references...");
        List<CtTypeReference<?>> typeRefs = Query.getElements(factory, new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            
            for (Map.Entry<String, String> migration : TRANSPORT_CLASS_MIGRATIONS.entrySet()) {
                String oldClass = migration.getKey();
                String newClass = migration.getValue();
                
                if (typeName.equals(oldClass)) {
                    System.out.println("Found type reference: " + oldClass + " -> " + newClass);
                    CtTypeReference<?> newRef = factory.Type().createReference(newClass);
                    typeRef.replace(newRef);
                    break;
                }
            }
        }
        
        // Output transformed code
        System.out.println("\nTransformed code:");
        launcher.setSourceOutputDirectory("/workspace/test-transformation/transformed");
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete!");
    }
}