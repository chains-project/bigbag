package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.compiler.Environment;
import java.util.*;

public class SimpleMain {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar transformation.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Starting Jakarta EE namespace migration transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Configure environment
        Environment env = launcher.getEnvironment();
        env.setAutoImports(false);
        env.setNoClasspath(true); // Don't need full classpath for simple transformation
        env.setComplianceLevel(11);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        transformInterceptorPackage(launcher.getFactory(), model);
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    private static void transformInterceptorPackage(spoon.reflect.factory.Factory factory, CtModel model) {
        // Find all compilation units
        List<CtCompilationUnit> compilationUnits = Query.getElements(factory, new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class));
        
        int transformedFiles = 0;
        
        for (CtCompilationUnit cu : compilationUnits) {
            boolean modified = false;
            
            // Get all imports
            List<CtImport> imports = cu.getImports();
            for (CtImport ctImport : imports) {
                CtReference ref = ctImport.getReference();
                if (ref instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                    String qualifiedName = typeRef.getQualifiedName();
                    
                    // Check if it's a javax.interceptor import
                    if (qualifiedName != null && qualifiedName.startsWith("javax.interceptor.")) {
                        String newName = qualifiedName.replace("javax.interceptor.", "jakarta.interceptor.");
                        typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                        
                        // Create new package reference
                        CtPackageReference pkgRef = typeRef.getFactory().createPackageReference();
                        pkgRef.setSimpleName("jakarta.interceptor");
                        typeRef.setPackage(pkgRef);
                        
                        modified = true;
                        System.out.println("Transformed import in " + cu.getFile().getName() + ": " + qualifiedName + " -> " + newName);
                    }
                }
            }
            
            // Also check for fully qualified type references in the code
            List<CtTypeReference<?>> typeRefs = Query.getElements(cu, new TypeFilter<CtTypeReference<?>>(CtTypeReference.class));
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith("javax.interceptor.")) {
                    String newName = qualifiedName.replace("javax.interceptor.", "jakarta.interceptor.");
                    typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                    
                    CtPackageReference pkgRef = typeRef.getFactory().createPackageReference();
                    pkgRef.setSimpleName("jakarta.interceptor");
                    typeRef.setPackage(pkgRef);
                    
                    modified = true;
                    System.out.println("Transformed type reference in " + cu.getFile().getName() + ": " + qualifiedName + " -> " + newName);
                }
            }
            
            if (modified) {
                transformedFiles++;
            }
        }
        
        System.out.println("\nTransformation Summary:");
        System.out.println("  Transformed files: " + transformedFiles);
        System.out.println("  Total compilation units processed: " + compilationUnits.size());
    }
}