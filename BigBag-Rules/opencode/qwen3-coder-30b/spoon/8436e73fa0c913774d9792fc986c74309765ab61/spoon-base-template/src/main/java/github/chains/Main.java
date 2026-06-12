package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        // Create launcher
        Launcher launcher = new Launcher();
        
        // Set the input directory 
        launcher.addInputResource("/workspace/billy");
        
        // Set environment options
        launcher.getEnvironment().setNoClasspath(true);
        
        // Add our processor
        launcher.addProcessor(new JaxWsMigrationProcessor());
        
        // Run the transformation
        launcher.run();
        
        System.out.println("JAX-WS migration completed!");
    }
}

class JaxWsMigrationProcessor extends AbstractProcessor<CtImport> {
    
    @Override
    public void process(CtImport ctImport) {
        // Get the imported type
        String importedTypeName = ctImport.getImportedType().toString();
        
        // Handle javax.jws package imports
        if (importedTypeName != null && importedTypeName.startsWith("javax.jws.")) {
            String newTypeName = importedTypeName.replace("javax.jws.", "jakarta.jws.");
            
            // Replace the import
            ctImport.setImportedType(getFactory().Type().createReference(newTypeName));
        }
        
        // Handle javax.xml.ws imports that might need updating 
        if (importedTypeName != null && importedTypeName.startsWith("javax.xml.ws.")) {
            String newTypeName = importedTypeName.replace("javax.xml.ws.", "jakarta.xml.ws.");
            
            // Replace the import
            ctImport.setImportedType(getFactory().Type().createReference(newTypeName));
        }
    }
}