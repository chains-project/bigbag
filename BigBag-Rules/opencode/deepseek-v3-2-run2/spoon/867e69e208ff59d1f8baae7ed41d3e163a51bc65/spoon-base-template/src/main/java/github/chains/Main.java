package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic transformation to fix libthrift 0.16.0 breaking changes.
 * 
 * Breaking changes addressed:
 * 1. TFramedTransport moved to org.apache.thrift.transport.layered package
 * 2. TFastFramedTransport also moved to layered package
 * 
 * NOTE: Additional manual fixes may be needed for:
 * - TFramedTransport constructors now throw TTransportException
 * - TProtocol.getMinSerializedSize(byte) is now abstract
 */
public class Main {
    // Type mappings for moved classes
    private static final Map<String, String> TYPE_MAPPINGS = new HashMap<>();
    
    static {
        TYPE_MAPPINGS.put("org.apache.thrift.transport.TFramedTransport", 
                         "org.apache.thrift.transport.layered.TFramedTransport");
        TYPE_MAPPINGS.put("org.apache.thrift.transport.TFastFramedTransport", 
                         "org.apache.thrift.transport.layered.TFastFramedTransport");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String sourcePath = args[0];
        System.out.println("Applying libthrift 0.16.0 API migration to: " + sourcePath);

        try {
            applyTransformations(sourcePath);
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage: java -jar spoon-base-1.0-SNAPSHOT.jar <source-directory>");
        System.err.println("Example: java -jar spoon-base-1.0-SNAPSHOT.jar /path/to/project/src/main/java");
        System.err.println("\nThis transformation fixes libthrift 0.16.0 breaking changes:");
        System.err.println("1. Updates moved classes:");
        for (Map.Entry<String, String> entry : TYPE_MAPPINGS.entrySet()) {
            System.err.println("   " + entry.getKey() + " -> " + entry.getValue());
        }
        System.err.println("\nNOTE: Additional manual fixes may be needed for:");
        System.err.println("- TFramedTransport constructors now throw TTransportException");
        System.err.println("- TProtocol.getMinSerializedSize(byte) is now abstract");
        System.err.println("- Update exception handling in calling code");
    }
    
    public static void applyTransformations(String sourcePath) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setLevel("OFF"); // Reduce logging
        
        launcher.buildModel();
        CtModel model = launcher.getModel();
        
        int totalChanges = 0;
        
        // 1. Update imports for moved classes
        totalChanges += updateImports(model, launcher);
        
        System.out.println("Total import updates: " + totalChanges);
        System.out.println("\nIMPORTANT: After running this transformation, you may need to:");
        System.out.println("1. Add try-catch blocks for TFramedTransport constructor calls");
        System.out.println("2. Implement getMinSerializedSize(byte) in TProtocol subclasses");
        System.out.println("3. Update lambda expressions that throw TTransportException");
        
        launcher.setSourceOutputDirectory(new java.io.File(sourcePath).getParent());
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete. Files saved.");
    }
    
    private static int updateImports(CtModel model, Launcher launcher) {
        int changes = 0;
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        for (CtImport imp : imports) {
            Object ref = imp.getReference();
            if (ref instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName != null && TYPE_MAPPINGS.containsKey(qualifiedName)) {
                    String newType = TYPE_MAPPINGS.get(qualifiedName);
                    
                    // Create new import with correct type reference
                    CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newType);
                    imp.setReference(newTypeRef);
                    
                    changes++;
                    System.out.println("Updated import: " + qualifiedName + " -> " + newType);
                }
            }
        }
        
        return changes;
    }
}