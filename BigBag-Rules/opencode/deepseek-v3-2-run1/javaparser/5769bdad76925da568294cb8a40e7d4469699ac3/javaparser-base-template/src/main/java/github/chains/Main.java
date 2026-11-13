package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking changes where a public field
 * is made private and replaced with a getter method.
 * 
 * This specific instance fixes: GHCompare.status -> GHCompare.getStatus()
 * but the pattern can be adapted for similar breaking changes.
 */
public class Main {
    
    // Configuration for the specific breaking change in github-api 1.313
    private static final String TARGET_TYPE_NAME = "org.kohsuke.github.GHCompare";
    private static final String FIELD_NAME = "status";
    private static final String GETTER_METHOD = "getStatus";
    
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms field access .status to method call .getStatus() on GHCompare objects");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Transformation: " + TARGET_TYPE_NAME + "." + FIELD_NAME + " -> " + TARGET_TYPE_NAME + "." + GETTER_METHOD + "()");
        
        // Setup JavaParser
        JavaParser javaParser = new JavaParser();
        
        // Find all Java files
        List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                int transformations = transformFile(javaFile, javaParser);
                if (transformations > 0) {
                    transformedFiles++;
                    totalTransformations += transformations;
                    System.out.println("  Modified " + javaFile + " (" + transformations + " transformations)");
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Files modified: " + transformedFiles);
        System.out.println("  Total transformations: " + totalTransformations);
    }
    
    private static List<Path> findJavaFiles(Path dir) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static int transformFile(Path javaFile, JavaParser javaParser) throws Exception {
        CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        FieldToGetterVisitor visitor = new FieldToGetterVisitor();
        cu.accept(visitor, null);
        
        if (visitor.transformations > 0) {
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(cu.toString());
            }
        }
        
        return visitor.transformations;
    }
    
    /**
     * Visitor that transforms field accesses to method calls.
     * Simple version: matches any field access with the given field name.
     * More sophisticated version would check the type of the expression.
     */
    private static class FieldToGetterVisitor extends ModifierVisitor<Void> {
        int transformations = 0;
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if field name matches
            if (FIELD_NAME.equals(n.getNameAsString())) {
                // Perform transformation: field -> getter
                MethodCallExpr methodCall = new MethodCallExpr(n.getScope(), GETTER_METHOD);
                transformations++;
                return methodCall;
            }
            
            return super.visit(n, arg);
        }
    }
}
