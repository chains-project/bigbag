package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic transformation rule to fix zip4j core package relocation issue.
 * 
 * Fixes the breaking change in zip4j 2.10.0 where ZipFile was moved from 
 * net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Launch Spoon on the source directory
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setComplianceLevel(8);
        CtModel model = launcher.buildModel();
        
        // Process imports to fix zip4j package relocation
        for (CtImport importStmt : model.getElements(new TypeFilter<>(CtImport.class))) {
            String importedType = importStmt.getReference().toString(); 
            if (importedType.contains("net.lingala.zip4j.core.ZipFile")) {
                // Create a new import statement for the new location
                importStmt.setReference(launcher.getFactory().Type().createReference("net.lingala.zip4j.ZipFile"));
            }
        }
        
        // Print the updated model to stdout
        launcher.getModel().getRootPackage().accept(new spoon.reflect.visitor.DefaultJavaPrettyPrinter(launcher.getEnvironment()));
    }
}