package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtReference;
import java.util.Map;
import java.util.HashMap;

public class Main {
    
    // Mapping from javax packages to jakarta packages
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Common javax to jakarta package mappings
        PACKAGE_MAPPINGS.put("javax.interceptor", "jakarta.interceptor");
        PACKAGE_MAPPINGS.put("javax.persistence", "jakarta.persistence");
        PACKAGE_MAPPINGS.put("javax.annotation", "jakarta.annotation");
        PACKAGE_MAPPINGS.put("javax.enterprise", "jakarta.enterprise");
        PACKAGE_MAPPINGS.put("javax.inject", "jakarta.inject");
        PACKAGE_MAPPINGS.put("javax.ejb", "jakarta.ejb");
        PACKAGE_MAPPINGS.put("javax.ws.rs", "jakarta.ws.rs");
        PACKAGE_MAPPINGS.put("javax.faces", "jakarta.faces");
        PACKAGE_MAPPINGS.put("javax.xml.bind", "jakarta.xml.bind");
        PACKAGE_MAPPINGS.put("javax.activation", "jakarta.activation");
        // Add more mappings as needed
    }
    
    // Processor that transforms javax imports to jakarta imports
    public static class JakartaImportProcessor extends AbstractProcessor<CtImport> {
        private int transformationCount = 0;
        
        @Override
        public void process(CtImport imp) {
            // Get the referenced type
            CtReference ref = imp.getReference();
            if (!(ref instanceof CtTypeReference)) {
                return;
            }
            
            CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName == null) {
                return;
            }
            
            // Check if this import starts with any javax package we need to replace
            for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
                String javaxPackage = mapping.getKey();
                String jakartaPackage = mapping.getValue();
                
                if (qualifiedName.startsWith(javaxPackage + ".")) {
                    // Create new qualified name with jakarta package
                    String newQualifiedName = qualifiedName.replace(javaxPackage, jakartaPackage);
                    
                    try {
                        // Create new type reference
                        CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newQualifiedName);
                        
                        // Update the import with the new reference
                        imp.setReference(newTypeRef);
                        
                        transformationCount++;
                        System.out.println("Transformed import: " + qualifiedName + " -> " + newQualifiedName);
                        break;
                    } catch (Exception e) {
                        System.err.println("Error transforming import " + qualifiedName + ": " + e.getMessage());
                    }
                }
            }
        }
        
        public int getTransformationCount() {
            return transformationCount;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main <source-directory>");
            System.err.println("Example: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        System.out.println("Processing source directory: " + sourcePath);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(sourcePath);
        
        // Create and add processor
        JakartaImportProcessor processor = new JakartaImportProcessor();
        launcher.addProcessor(processor);
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Total transformations applied: " + processor.getTransformationCount());
        
        // Write the transformed code back to files
        launcher.setSourceOutputDirectory(sourcePath);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Modified files saved.");
    }
}