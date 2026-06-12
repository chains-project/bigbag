package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.compiler.Environment;
import java.util.*;

public class Main {
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
        env.setNoClasspath(false);
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
        // Find all import declarations
        List<CtImport> imports = Query.getElements(factory, new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference() != null;
            }
        });
        
        // Track transformations for reporting
        int transformedImports = 0;
        int transformedTypeRefs = 0;
        
        // Transform imports
        for (CtImport ctImport : imports) {
            CtReference ref = ctImport.getReference();
            if (ref instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                String qualifiedName = typeRef.getQualifiedName();
                
                // Check if it's a javax.interceptor import
                if (qualifiedName.startsWith("javax.interceptor.")) {
                    String newName = qualifiedName.replace("javax.interceptor.", "jakarta.interceptor.");
                    typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                    
                    // Create new package reference
                    CtPackageReference pkgRef = typeRef.getFactory().createPackageReference();
                    pkgRef.setSimpleName("jakarta.interceptor");
                    typeRef.setPackage(pkgRef);
                    
                    transformedImports++;
                    System.out.println("Transformed import: " + qualifiedName + " -> " + newName);
                }
            }
        }
        
        // Find all type references in the code (not just imports)
        List<CtTypeReference<?>> typeRefs = Query.getElements(factory, 
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    return element.getQualifiedName() != null;
                }
            });
        
        // Transform type references in code
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // Check if it's a javax.interceptor type reference
            if (qualifiedName != null && qualifiedName.startsWith("javax.interceptor.")) {
                String newName = qualifiedName.replace("javax.interceptor.", "jakarta.interceptor.");
                
                // Update the type reference
                typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                
                // Create new package reference
                CtPackageReference pkgRef = typeRef.getFactory().createPackageReference();
                pkgRef.setSimpleName("jakarta.interceptor");
                typeRef.setPackage(pkgRef);
                
                transformedTypeRefs++;
                System.out.println("Transformed type reference: " + qualifiedName + " -> " + newName);
            }
        }
        
        // Find all method invocations that might use interceptor types
        List<CtMethod<?>> methods = Query.getElements(factory, new TypeFilter<CtMethod<?>>(CtMethod.class));
        for (CtMethod<?> method : methods) {
            // Check method parameters
            for (CtParameter<?> param : method.getParameters()) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType != null && paramType.getQualifiedName() != null) {
                    String qualifiedName = paramType.getQualifiedName();
                    if (qualifiedName.startsWith("javax.interceptor.")) {
                        String newName = qualifiedName.replace("javax.interceptor.", "jakarta.interceptor.");
                        paramType.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                        
                        CtPackageReference pkgRef = paramType.getFactory().createPackageReference();
                        pkgRef.setSimpleName("jakarta.interceptor");
                        paramType.setPackage(pkgRef);
                        
                        System.out.println("Transformed parameter type: " + qualifiedName + " -> " + newName);
                    }
                }
            }
            
            // Check return type
            CtTypeReference<?> returnType = method.getType();
            if (returnType != null && returnType.getQualifiedName() != null) {
                String qualifiedName = returnType.getQualifiedName();
                if (qualifiedName.startsWith("javax.interceptor.")) {
                    String newName = qualifiedName.replace("javax.interceptor.", "jakarta.interceptor.");
                    returnType.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                    
                    CtPackageReference pkgRef = returnType.getFactory().createPackageReference();
                    pkgRef.setSimpleName("jakarta.interceptor");
                    returnType.setPackage(pkgRef);
                    
                    System.out.println("Transformed return type: " + qualifiedName + " -> " + newName);
                }
            }
        }
        
        System.out.println("\nTransformation Summary:");
        System.out.println("  Transformed imports: " + transformedImports);
        System.out.println("  Transformed type references: " + transformedTypeRefs);
    }
}