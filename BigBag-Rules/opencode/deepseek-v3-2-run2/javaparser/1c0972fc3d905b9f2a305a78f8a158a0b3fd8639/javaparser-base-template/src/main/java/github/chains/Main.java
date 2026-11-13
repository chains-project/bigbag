package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for maven-dependency-tree 3.2.1 breaking changes.
 * 
 * This transformation handles the removal of Maven31DependencyGraphBuilder class
 * by removing imports and replacing object creation expressions with dependency injection.
 * 
 * The transformation is parameterized by:
 * - REMOVED_CLASS: The fully qualified class name that was removed
 * - SOURCE_DIR: The directory containing source files to transform
 */
public class Main {
    
    // Configuration parameters - these should be changed for different projects
    private static final String REMOVED_CLASS = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String SOURCE_DIR = "/workspace/license-maven-plugin";
    
    public static void main(String[] args) throws IOException {
        if (args.length >= 2) {
            // Use command line arguments if provided
            String removedClass = args[0];
            String sourceDir = args[1];
            transformProject(removedClass, sourceDir);
        } else {
            // Use default configuration
            transformProject(REMOVED_CLASS, SOURCE_DIR);
        }
    }
    
    private static void transformProject(String removedClass, String sourceDir) throws IOException {
        System.out.println("Transforming project with removed class: " + removedClass);
        System.out.println("Source directory: " + sourceDir);
        
        List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int[] transformedFiles = {0};
        int[] removedImports = {0};
        int[] replacedCreations = {0};
        
        for (Path javaFile : javaFiles) {
            boolean[] fileModified = {false};
            
            try {
                JavaParser parser = new JavaParser();
                ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
                if (!parseResult.isSuccessful()) {
                    System.err.println("Failed to parse " + javaFile + ": " + parseResult.getProblems());
                    continue;
                }
                CompilationUnit cu = parseResult.getResult().orElse(null);
                if (cu == null) {
                    System.err.println("No compilation unit found in " + javaFile);
                    continue;
                }
                
                // Remove imports of the removed class
                List<ImportDeclaration> importsToRemove = new ArrayList<>();
                for (ImportDeclaration importDecl : cu.getImports()) {
                    if (importDecl.getNameAsString().equals(removedClass)) {
                        importsToRemove.add(importDecl);
                        removedImports[0]++;
                        fileModified[0] = true;
                    }
                }
                importsToRemove.forEach(Node::remove);
                
                // Replace object creation expressions with dependency injection pattern
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(ObjectCreationExpr n, Void arg) {
                        if (n.getType().asString().equals(getSimpleClassName(removedClass))) {
                            replacedCreations[0]++;
                            fileModified[0] = true;
                            // For Maven31DependencyGraphBuilder, the proper fix is to use
                            // dependency injection instead of direct instantiation
                            // We replace with null and let the calling code handle it
                            return null;
                        }
                        return super.visit(n, arg);
                    }
                }, null);
                
                if (fileModified[0]) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles[0]++;
                    System.out.println("Transformed: " + javaFile);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation completed:");
        System.out.println("- Files transformed: " + transformedFiles[0]);
        System.out.println("- Imports removed: " + removedImports[0]);
        System.out.println("- Object creations replaced: " + replacedCreations[0]);
        
        if (replacedCreations[0] > 0) {
            System.out.println("\nIMPORTANT: Object creations were replaced with null.");
            System.out.println("You need to manually fix these locations to use dependency injection.");
            System.out.println("For Maven31DependencyGraphBuilder, use @Inject or @Component annotations.");
        }
    }
    
    private static List<Path> findJavaFiles(Path rootDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(rootDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static String getSimpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
}