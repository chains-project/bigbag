package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtLiteral;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in com.jcabi.aspects.Tv class.
 * This transformation replaces Tv.SEVEN, Tv.TEN, and Tv.MILLION with their literal values.
 */
public class Main {
    public static void main(String[] args) {
        // Check if source directory is provided
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/fixed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Process the code
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Replace Tv.SEVEN with 7
        replaceTvConstant(factory, "SEVEN", 7);
        
        // Replace Tv.TEN with 10
        replaceTvConstant(factory, "TEN", 10);
        
        // Replace Tv.MILLION with 1000000
        replaceTvConstant(factory, "MILLION", 1000000);
        
        // Write the modified code back
        launcher.setSourceOutputDirectory(sourceDirectory + "/fixed");
        launcher.process();
        
        System.out.println("Transformation complete. Fixed files are in " + sourceDirectory + "/fixed");
    }
    
    /**
     * Replace usage of a Tv constant with its literal value
     */
    private static void replaceTvConstant(Factory factory, String constantName, int value) {
        // Find all field reads of the form Tv.constantName
        List<CtFieldRead> fieldReads = factory.getModel().getRootPackage()
            .filterChildren(new TypeFilter<>(CtFieldRead.class))
            .list();
            
        // Process each field read
        for (CtFieldRead fieldRead : fieldReads) {
            if (fieldRead.getVariable() != null) {
                CtFieldReference fieldRef = fieldRead.getVariable();
                if (fieldRef.getDeclaringType() != null && 
                    fieldRef.getDeclaringType().getQualifiedName().equals("com.jcabi.aspects.Tv") &&
                    fieldRef.getSimpleName().equals(constantName)) {
                    CtLiteral<Integer> literal = factory.Code().createLiteral(value);
                    fieldRead.replace(literal);
                }
            }
        }
    }
}