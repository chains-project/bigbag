package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // This is a generic transformation for CertificationPermission API change
        // from eu.europa.esig.dss.pades to eu.europa.esig.dss.enumerations
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon factory
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/processed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Process the code
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all references to CertificationPermission and replace them with the correct package
        model.getElements(new TypeFilter<>(CtTypeReference.class))
            .stream()
            .filter(ref -> ref.toString().equals("eu.europa.esig.dss.pades.CertificationPermission"))
            .forEach(ref -> {
                // Create a new reference with the correct package
                CtTypeReference<?> newRef = factory.Type().createReference("eu.europa.esig.dss.enumerations.CertificationPermission");
                // Set the package and simple name
                ref.setPackage(newRef.getPackage());
                ref.setSimpleName(newRef.getSimpleName());
            });
        
        // Generate the modified code
        launcher.process();
        
        System.out.println("Transformation completed successfully");
    }
}