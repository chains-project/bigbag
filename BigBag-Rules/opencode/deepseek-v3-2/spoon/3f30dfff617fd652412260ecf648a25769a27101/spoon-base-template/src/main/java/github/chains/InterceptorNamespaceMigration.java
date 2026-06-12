package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.compiler.Environment;
import java.util.*;

/**
 * Generic Spoon transformation rule for migrating from javax.interceptor
 * to jakarta.interceptor namespace (Jakarta EE 9+).
 * 
 * This transformation handles:
 * 1. Import statements: javax.interceptor.* -> jakarta.interceptor.*
 * 2. Fully qualified type references in code
 * 3. Method parameter types and return types
 * 
 * The transformation is generic and can be applied to any Java project
 * affected by the Jakarta EE namespace change.
 */
public class InterceptorNamespaceMigration {
    
    /**
     * Main entry point for the transformation.
     * 
     * @param args Command line arguments: <source-dir> <output-dir>
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("================================================");
        System.out.println("Jakarta EE Interceptor Namespace Migration Tool");
        System.out.println("================================================");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println();
        
        try {
            int transformedCount = applyTransformation(sourceDir, outputDir);
            System.out.println("\n✅ Transformation completed successfully!");
            System.out.println("   Transformed " + transformedCount + " compilation units");
            System.out.println("\nThe transformed code is available in: " + outputDir);
        } catch (Exception e) {
            System.err.println("\n❌ Transformation failed:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage: java -jar transformation.jar <source-directory> <output-directory>");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java -jar transformation.jar /path/to/project/src /path/to/transformed/src");
        System.err.println();
        System.err.println("Description:");
        System.err.println("  This tool migrates javax.interceptor.* imports and references");
        System.err.println("  to jakarta.interceptor.* for Jakarta EE 9+ compatibility.");
    }
    
    /**
     * Applies the namespace migration transformation.
     * 
     * @param sourceDir Source code directory
     * @param outputDir Output directory for transformed code
     * @return Number of compilation units transformed
     */
    public static int applyTransformation(String sourceDir, String outputDir) {
        // Create launcher with no classpath to avoid dependency issues
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Configure environment
        Environment env = launcher.getEnvironment();
        env.setAutoImports(false);
        env.setNoClasspath(true);
        env.setComplianceLevel(11);
        env.setCommentEnabled(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        int transformedCount = transformInterceptorNamespace(launcher.getFactory(), model);
        
        // Write transformed code
        launcher.prettyprint();
        
        return transformedCount;
    }
    
    /**
     * Core transformation logic.
     * 
     * @param factory Spoon factory
     * @param model Spoon model
     * @return Number of compilation units transformed
     */
    private static int transformInterceptorNamespace(spoon.reflect.factory.Factory factory, CtModel model) {
        List<CtCompilationUnit> compilationUnits = Query.getElements(factory, 
            new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class));
        
        int transformedUnits = 0;
        
        for (CtCompilationUnit cu : compilationUnits) {
            boolean modified = transformCompilationUnit(cu);
            if (modified) {
                transformedUnits++;
            }
        }
        
        return transformedUnits;
    }
    
    /**
     * Transform a single compilation unit.
     * 
     * @param cu Compilation unit to transform
     * @return true if the unit was modified
     */
    private static boolean transformCompilationUnit(CtCompilationUnit cu) {
        boolean modified = false;
        String fileName = cu.getFile() != null ? cu.getFile().getName() : "unknown";
        
        // Transform imports
        List<CtImport> imports = cu.getImports();
        for (CtImport ctImport : imports) {
            if (transformImport(ctImport)) {
                modified = true;
                System.out.println("  • Transformed import in " + fileName);
            }
        }
        
        // Transform type references in the code
        List<CtTypeReference<?>> typeRefs = Query.getElements(cu, 
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class));
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            if (transformTypeReference(typeRef)) {
                modified = true;
                System.out.println("  • Transformed type reference in " + fileName);
            }
        }
        
        return modified;
    }
    
    /**
     * Transform an import statement.
     * 
     * @param ctImport Import to transform
     * @return true if the import was transformed
     */
    private static boolean transformImport(CtImport ctImport) {
        CtReference ref = ctImport.getReference();
        if (ref instanceof CtTypeReference) {
            CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
            return transformTypeReference(typeRef);
        }
        return false;
    }
    
    /**
     * Transform a type reference.
     * 
     * @param typeRef Type reference to transform
     * @return true if the type reference was transformed
     */
    private static boolean transformTypeReference(CtTypeReference<?> typeRef) {
        String qualifiedName = typeRef.getQualifiedName();
        
        // Check if it's a javax.interceptor type
        if (qualifiedName != null && qualifiedName.startsWith("javax.interceptor.")) {
            // Replace javax.interceptor with jakarta.interceptor
            String newName = qualifiedName.replace("javax.interceptor.", "jakarta.interceptor.");
            
            // Update simple name
            String simpleName = newName.substring(newName.lastIndexOf('.') + 1);
            typeRef.setSimpleName(simpleName);
            
            // Update package reference
            CtPackageReference pkgRef = typeRef.getFactory().createPackageReference();
            pkgRef.setSimpleName("jakarta.interceptor");
            typeRef.setPackage(pkgRef);
            
            return true;
        }
        
        return false;
    }
}