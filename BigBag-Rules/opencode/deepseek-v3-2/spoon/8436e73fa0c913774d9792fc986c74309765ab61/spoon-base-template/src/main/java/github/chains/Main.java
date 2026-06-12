package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Jakarta EE imports in: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
        System.out.println("Found " + compilationUnits.size() + " compilation units");
        int totalImportsUpdated = 0;
        int totalFilesProcessed = 0;
        
        for (CtCompilationUnit cu : compilationUnits) {
            System.out.println("Processing: " + cu.getFile().getAbsolutePath());
            List<CtImport> imports = cu.getImports();
            System.out.println("  Has " + imports.size() + " imports");
            List<CtImport> newImports = new ArrayList<>();
            boolean modified = false;
            
            for (CtImport imp : imports) {
                CtReference ref = imp.getReference();
                String importString = ref.toString();
                System.out.println("  Checking import: " + importString);
                
                if (shouldTransformImport(importString)) {
                    String transformedImport = transformImport(importString);
                    System.out.println("  Should transform to: " + transformedImport);
                    if (!transformedImport.equals(importString)) {
                        // Create new import based on the import kind
                        CtReference newRef;
                        if (importString.endsWith(".*")) {
                            // For wildcard imports
                            String packageName = transformedImport.substring(0, transformedImport.length() - 2);
                            CtPackageReference pkgRef = launcher.getFactory().Package().createReference(packageName);
                            newRef = pkgRef;
                        } else {
                            // For regular type imports
                            newRef = launcher.getFactory().createReference(transformedImport);
                        }
                        CtImport newImport = launcher.getFactory().createImport(newRef);
                        newImports.add(newImport);
                        modified = true;
                        totalImportsUpdated++;
                        continue;
                    }
                }
                newImports.add(imp);
            }
            
            if (modified) {
                cu.setImports(newImports);
                launcher.prettyprint();
                totalFilesProcessed++;
                System.out.println("  Modified file");
            }
        }
        
        System.out.println("Transformation complete!");
        System.out.println("Files processed: " + totalFilesProcessed);
        System.out.println("Imports updated: " + totalImportsUpdated);
    }
    
    private static boolean shouldTransformImport(String importString) {
        return importString.startsWith("javax.jws.") ||
               importString.startsWith("javax.xml.ws.") ||
               importString.startsWith("javax.xml.bind.") ||
               importString.startsWith("javax.xml.soap.") ||
               importString.startsWith("javax.jws.soap.");
    }
    
    private static String transformImport(String importString) {
        if (importString.startsWith("javax.jws.")) {
            return importString.replace("javax.jws.", "jakarta.jws.");
        } else if (importString.startsWith("javax.xml.ws.")) {
            return importString.replace("javax.xml.ws.", "jakarta.xml.ws.");
        } else if (importString.startsWith("javax.xml.bind.")) {
            return importString.replace("javax.xml.bind.", "jakarta.xml.bind.");
        } else if (importString.startsWith("javax.xml.soap.")) {
            return importString.replace("javax.xml.soap.", "jakarta.xml.soap.");
        } else if (importString.startsWith("javax.jws.soap.")) {
            return importString.replace("javax.jws.soap.", "jakarta.jws.soap.");
        }
        return importString;
    }
    
    
}