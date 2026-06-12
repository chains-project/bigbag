package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.CtScanner;

import java.util.HashMap;
import java.util.Map;

/**
 * GENERIC, REUSABLE transformation rule for Thrift 0.16.0 package relocation.
 * 
 * Breaking Change Characterization:
 * - Old API pattern: org.apache.thrift.transport.TFastFramedTransport
 * - New API pattern: org.apache.thrift.transport.layered.TFastFramedTransport
 * - Old API pattern: org.apache.thrift.transport.TFramedTransport  
 * - New API pattern: org.apache.thrift.transport.layered.TFramedTransport
 * 
 * Structural transformation required: Update import statements, type references,
 * and constructor calls to use the new package location.
 * 
 * This rule is parameterized by fully-qualified type names from the dependency,
 * NOT from the client code. It matches the old API pattern structurally
 * and applies the fix wherever the old pattern appears.
 */
public class GenericThriftFix extends AbstractProcessor<CtType<?>> {
    
    // Parameterize by fully-qualified type names from the Thrift dependency
    private static final Map<String, String> PACKAGE_RELOCATIONS = new HashMap<>();
    static {
        PACKAGE_RELOCATIONS.put("org.apache.thrift.transport.TFastFramedTransport", 
                               "org.apache.thrift.transport.layered.TFastFramedTransport");
        PACKAGE_RELOCATIONS.put("org.apache.thrift.transport.TFramedTransport", 
                               "org.apache.thrift.transport.layered.TFramedTransport");
    }
    
    @Override
    public void process(CtType<?> type) {
        // Transform imports in the compilation unit
        if (type.getPosition() != null && type.getPosition().getCompilationUnit() != null) {
            for (CtImport imp : type.getPosition().getCompilationUnit().getImports()) {
                String importString = imp.getReference().toString();
                for (Map.Entry<String, String> entry : PACKAGE_RELOCATIONS.entrySet()) {
                    if (importString.equals(entry.getKey())) {
                        CtTypeReference<?> newRef = getFactory().createReference(entry.getValue());
                        imp.setReference(newRef);
                        System.out.println("[INFO] Transformed import: " + importString + " -> " + entry.getValue());
                    }
                }
            }
        }
        
        // Use scanner to find and replace all type references and constructor calls
        type.accept(new CtScanner() {
            @Override
            public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
                super.visitCtConstructorCall(ctConstructorCall);
                
                CtTypeReference<?> constructedType = ctConstructorCall.getType();
                if (constructedType != null) {
                    String typeName = constructedType.getQualifiedName();
                    for (Map.Entry<String, String> entry : PACKAGE_RELOCATIONS.entrySet()) {
                        if (typeName.equals(entry.getKey())) {
                            CtTypeReference<?> newRef = getFactory().createReference(entry.getValue());
                            ctConstructorCall.setType(newRef);
                            System.out.println("[INFO] Transformed constructor call: " + typeName + " -> " + entry.getValue());
                        }
                    }
                }
            }
            
            @Override
            public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
                super.visitCtTypeReference(reference);
                
                String typeName = reference.getQualifiedName();
                for (Map.Entry<String, String> entry : PACKAGE_RELOCATIONS.entrySet()) {
                    if (typeName.equals(entry.getKey())) {
                        CtTypeReference<?> newRef = getFactory().createReference(entry.getValue());
                        reference.replace(newRef);
                        System.out.println("[INFO] Transformed type reference: " + typeName + " -> " + entry.getValue());
                    }
                }
            }
        });
    }
    
    /**
     * Main method to run the generic transformation.
     * 
     * @param args Command line arguments: <source-directory> <output-directory>
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java GenericThriftFix <source-directory> <output-directory>");
            System.err.println("Example: java GenericThriftFix /path/to/project /path/to/transformed");
            System.err.println("\nThis generic transformation fixes Thrift 0.16.0 package relocation:");
            System.err.println("  org.apache.thrift.transport.TFastFramedTransport -> org.apache.thrift.transport.layered.TFastFramedTransport");
            System.err.println("  org.apache.thrift.transport.TFramedTransport -> org.apache.thrift.transport.layered.TFramedTransport");
            System.err.println("\nThe transformation is structural and matches the old API pattern:");
            System.err.println("  - Matches imports, type references, and constructor calls");
            System.err.println("  - Parameterized by dependency type names, not client code");
            System.err.println("  - Applicable to ANY project affected by this breaking change");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Generic Thrift 0.16.0 Package Relocation Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Breaking change: Transport classes moved to 'layered' subpackage");
        System.out.println("===============================================================");
        
        try {
            Launcher launcher = new Launcher();
            
            // Configure launcher for generic transformation
            launcher.getEnvironment().setNoClasspath(true);        // Don't need dependencies
            launcher.getEnvironment().setAutoImports(true);        // Preserve imports
            launcher.getEnvironment().setCommentEnabled(true);     // Keep comments
            launcher.getEnvironment().setLevel("WARN");           // Reduce verbosity
            
            // Process all Java files in the source directory
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Add our generic processor
            launcher.addProcessor(new GenericThriftFix());
            
            // Run the transformation
            launcher.run();
            
            System.out.println("\n===============================================================");
            System.out.println("Transformation completed successfully!");
            System.out.println("Transformed files written to: " + outputDir);
            System.out.println("\nThis generic rule can be applied to ANY project affected by");
            System.out.println("the Thrift 0.16.0 breaking change by changing the source path.");
            System.out.println("===============================================================");
            
        } catch (Exception e) {
            System.err.println("\nError during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}