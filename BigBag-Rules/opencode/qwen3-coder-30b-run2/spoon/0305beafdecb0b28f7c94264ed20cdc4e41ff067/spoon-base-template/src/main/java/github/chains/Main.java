package github.chains;

import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;

/**
 * Generic Spoon transformation to fix MySQL connector timeout exception import issues.
 * This transformation addresses breaking changes in MySQL Connector/J where
 * com.mysql.jdbc.exceptions.MySQLTimeoutException was moved to com.mysql.cj.jdbc.exceptions.MySQLTimeoutException
 */
public class Main {

    public static void main(String[] args) {
        // Usage: java -cp target/classes:~/.m2/repository/fr/inria/gforge/spoon/spoon-core/11.2.1/spoon-core-11.2.1.jar github.chains.Main /path/to/source/directory
        if (args.length < 1) {
            System.err.println("Usage: Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        spoon.Launcher launcher = new spoon.Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply the transformation
        MySQLExceptionFixProcessor processor = new MySQLExceptionFixProcessor();
        model.processWith(processor);
        
        // Write back the modified files
        launcher.process();
        
        System.out.println("Transformation completed.");
    }
    
    /**
     * Processor to fix MySQL timeout exception references
     */
    public static class MySQLExceptionFixProcessor extends AbstractProcessor<CtElement> {
        
        @Override
        public void process(CtElement element) {
            // Handle imports
            if (element instanceof CtImport) {
                CtImport ctImport = (CtImport) element;
                CtReference reference = ctImport.getReference();
                
                // Check for old MySQL timeout exception import
                if (reference != null && reference.toString().contains("com.mysql.jdbc.exceptions.MySQLTimeoutException")) {
                    // Replace with new import
                    Factory factory = element.getFactory();
                    CtTypeReference<?> newRef = factory.Type().createReference("com.mysql.cj.jdbc.exceptions.MySQLTimeoutException");
                    ctImport.setReference(newRef);
                }
            }
            
            // Handle direct references to the old class
            if (element instanceof CtTypeAccess) {
                CtTypeAccess typeAccess = (CtTypeAccess) element;
                CtTypeReference<?> typeRef = typeAccess.getAccessedType();
                
                if (typeRef != null && typeRef.getQualifiedName().equals("com.mysql.jdbc.exceptions.MySQLTimeoutException")) {
                    // Change the type reference to the new location
                    Factory factory = element.getFactory();
                    CtTypeReference<?> newRef = factory.Type().createReference("com.mysql.cj.jdbc.exceptions.MySQLTimeoutException");
                    typeAccess.setAccessedType(newRef);
                }
            }
        }
    }
}