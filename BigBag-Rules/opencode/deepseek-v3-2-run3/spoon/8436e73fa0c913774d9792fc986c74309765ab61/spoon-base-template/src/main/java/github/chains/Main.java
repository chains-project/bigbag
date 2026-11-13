package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jakarta EE migration transformation to: " + sourceDir);
        
        // Create Spoon launcher
        SpoonAPI spoon = new Launcher();
        spoon.addInputResource(sourceDir);
        spoon.getEnvironment().setNoClasspath(true);
        spoon.getEnvironment().setAutoImports(false);
        spoon.getEnvironment().setCommentEnabled(true);
        
        try {
            // Build model
            CtModel model = spoon.buildModel();
            
            // Get all compilation units
            List<CtCompilationUnit> compilationUnits = new ArrayList<>(model.getRootPackage().getElements(new TypeFilter<>(CtCompilationUnit.class)));
            
            int importChanges = 0;
            int fileChanges = 0;
            
            for (CtCompilationUnit cu : compilationUnits) {
                boolean fileModified = false;
                List<CtImport> imports = cu.getImports();
                
                for (CtImport imp : imports) {
                    String importStr = imp.toString();
                    
                    // Check for javax.jws imports
                    if (importStr.startsWith("import javax.jws")) {
                        String newImport = importStr.replace("javax.jws", "jakarta.jws");
                        imp.replace(spoon.getFactory().createImport(spoon.getFactory().createReference(newImport.substring(7, newImport.length() - 1))));
                        importChanges++;
                        fileModified = true;
                        System.out.println("Transformed: " + importStr + " -> " + newImport);
                    }
                    // Check for javax.jws.soap imports
                    else if (importStr.startsWith("import javax.jws.soap")) {
                        String newImport = importStr.replace("javax.jws.soap", "jakarta.jws.soap");
                        imp.replace(spoon.getFactory().createImport(spoon.getFactory().createReference(newImport.substring(7, newImport.length() - 1))));
                        importChanges++;
                        fileModified = true;
                        System.out.println("Transformed: " + importStr + " -> " + newImport);
                    }
                    // Check for javax.xml.ws imports
                    else if (importStr.startsWith("import javax.xml.ws")) {
                        String newImport = importStr.replace("javax.xml.ws", "jakarta.xml.ws");
                        imp.replace(spoon.getFactory().createImport(spoon.getFactory().createReference(newImport.substring(7, newImport.length() - 1))));
                        importChanges++;
                        fileModified = true;
                        System.out.println("Transformed: " + importStr + " -> " + newImport);
                    }
                    // Check for javax.xml.bind imports
                    else if (importStr.startsWith("import javax.xml.bind")) {
                        String newImport = importStr.replace("javax.xml.bind", "jakarta.xml.bind");
                        imp.replace(spoon.getFactory().createImport(spoon.getFactory().createReference(newImport.substring(7, newImport.length() - 1))));
                        importChanges++;
                        fileModified = true;
                        System.out.println("Transformed: " + importStr + " -> " + newImport);
                    }
                    // Check for javax.xml.datatype imports
                    else if (importStr.startsWith("import javax.xml.datatype")) {
                        String newImport = importStr.replace("javax.xml.datatype", "jakarta.xml.datatype");
                        imp.replace(spoon.getFactory().createImport(spoon.getFactory().createReference(newImport.substring(7, newImport.length() - 1))));
                        importChanges++;
                        fileModified = true;
                        System.out.println("Transformed: " + importStr + " -> " + newImport);
                    }
                }
                
                if (fileModified) {
                    fileChanges++;
                }
            }
            
            // Write all changes back to files
            if (fileChanges > 0) {
                spoon.prettyprint();
            }
            
            System.out.println("\nTransformation complete!");
            System.out.println("Total import statements transformed: " + importChanges);
            System.out.println("Total files modified: " + fileChanges);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}