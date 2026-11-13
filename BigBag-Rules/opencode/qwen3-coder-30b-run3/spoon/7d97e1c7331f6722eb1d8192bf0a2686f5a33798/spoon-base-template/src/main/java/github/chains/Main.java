package github.chains;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic transformation to fix breaking changes in jcabi-aspects dependency.
 * Specifically addresses removal of com.jcabi.aspects.Tv class in version 0.25.1
 */
public class Main {
    public static void main(String[] args) {
        // Default to the jcabi-simpledb project path
        String sourcePath = "/workspace/jcabi-simpledb";
        
        // Allow override via command line argument
        if (args.length > 0) {
            sourcePath = args[0];
        }
        
        System.out.println("Applying transformation to: " + sourcePath);
        fixTvClassReferences(sourcePath);
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Fixes references to com.jcabi.aspects.Tv class by replacing with numeric constants
     */
    public static void fixTvClassReferences(String sourcePath) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        launcher.setSourceOutputDirectory(sourcePath + "/src/main/java_fixed");
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all field accesses to Tv.class
        List<CtFieldAccess<?>> tvFieldAccesses = model.getElements(new TypeFilter<>(CtFieldAccess.class));
        
        for (CtFieldAccess<?> fieldAccess : tvFieldAccesses) {
            if (fieldAccess.getVariable() != null) {
                CtFieldReference<?> fieldRef = fieldAccess.getVariable();
                if (fieldRef.getDeclaringType() != null) {
                    String declaringTypeName = fieldRef.getDeclaringType().getQualifiedName();
                    
                    // Replace Tv constants with numeric equivalents
                    if ("com.jcabi.aspects.Tv".equals(declaringTypeName)) {
                        String fieldName = fieldRef.getSimpleName();
                        Integer replacementValue = getReplacementValue(fieldName);
                        
                        if (replacementValue != null) {
                            // Replace with literal integer value
                            fieldAccess.replace(fieldAccess.getFactory().Code().createLiteral(replacementValue));
                        }
                    }
                }
            }
        }
        
        // Remove the Tv import statement if it exists
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null && 
                "com.jcabi.aspects.Tv".equals(imp.getReference().getQualifiedName())) {
                imp.delete();
            }
        }
        
        // Print what we fixed for verification
        System.out.println("Fixed Tv field accesses in the codebase");
        
        // Generate the modified files
        launcher.prettyprint();
    }
    
    /**
     * Get the numeric replacement value for a Tv constant
     */
    private static Integer getReplacementValue(String fieldName) {
        switch (fieldName) {
            case "TEN":
                return 10;
            case "EIGHT": 
                return 8;
            case "ZERO":
                return 0;
            case "ONE":
                return 1;
            default:
                return null;
        }
    }
}