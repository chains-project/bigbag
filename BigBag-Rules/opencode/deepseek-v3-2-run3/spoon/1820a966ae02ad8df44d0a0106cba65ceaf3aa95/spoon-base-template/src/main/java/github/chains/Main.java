package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp spoon-transformation.jar github.chains.Main <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transformation for logback-classic 1.4.4 / slf4j-api compatibility");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find and process all compilation units
        List<CtCompilationUnit> units = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
        for (CtCompilationUnit unit : units) {
            processUnit(unit);
        }
        
        launcher.prettyprint();
        System.out.println("Transformation complete.");
    }
    
    private static void processUnit(CtCompilationUnit unit) {
        boolean modified = false;
        
        // Remove ch.qos.logback.classic.Logger imports
        for (CtImport imp : unit.getImports()) {
            if (imp.toString().contains("ch.qos.logback.classic.Logger")) {
                try {
                    unit.removeImport(imp);
                    modified = true;
                } catch (Exception e) {
                    // Continue
                }
            }
        }
        
        // Process all type references in this unit
        List<CtTypeReference<?>> typeRefs = unit.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            if ("ch.qos.logback.classic.Logger".equals(typeRef.getQualifiedName())) {
                // Found reference to Logger class
                // Try to change it to org.slf4j.Logger
                try {
                    CtTypeReference<?> slf4jLogger = typeRef.getFactory().createCtTypeReference();
                    slf4jLogger.setSimpleName("Logger");
                    slf4jLogger.setPackage(typeRef.getFactory().createReference("org.slf4j"));
                    
                    // Replace the type reference
                    typeRef.replace(slf4jLogger);
                    modified = true;
                } catch (Exception e) {
                    System.err.println("Could not replace Logger reference: " + e.getMessage());
                }
            }
        }
        
        if (modified) {
            System.out.println("Processed: " + unit.getFile().getName());
        }
    }
}