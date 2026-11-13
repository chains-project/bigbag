package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import spoon.Launcher;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-folder>");
            System.exit(1);
        }
        
        String sourceFolder = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceFolder);
        
        // Process with our transform
        launcher.addProcessor(new JasyptFixProcessor());
        
        // Build model and process
        launcher.buildModel();
        launcher.process();
        
        System.out.println("Jasypt dependency fix transformation completed.");
    }
    
    public static class JasyptFixProcessor extends AbstractProcessor<CtImport> {
        @Override
        public void process(CtImport ctImport) {
            String importedTypeName = ctImport.getReference().toString();
            
            // Fix the PasswordEncoder import
            if ("org.jasypt.spring.security.PasswordEncoder".equals(importedTypeName)) {
                // Replace with correct import - but for now just remove it
                ctImport.delete();
                System.out.println("Removed PasswordEncoder import: " + importedTypeName);
            }
            
            // Fix the PBEPasswordEncoder import
            if ("org.jasypt.spring.security.PBEPasswordEncoder".equals(importedTypeName)) {
                // Replace with correct import - but for now just remove it
                ctImport.delete();
                System.out.println("Removed PBEPasswordEncoder import: " + importedTypeName);
            }
            
            // Fix the acegi import
            if ("org.acegisecurity.providers.encoding.PasswordEncoder".equals(importedTypeName)) {
                // Remove acegi import as well
                ctImport.delete();
                System.out.println("Removed acegi PasswordEncoder import: " + importedTypeName);
            }
        }
    }
}