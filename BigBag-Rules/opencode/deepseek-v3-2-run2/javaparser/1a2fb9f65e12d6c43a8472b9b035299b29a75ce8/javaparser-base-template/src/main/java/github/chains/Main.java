package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for migrating from javax.validation to jakarta.validation
 * This transformation handles the package namespace change from Java EE to Jakarta EE
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String OLD_PACKAGE_PREFIX = "javax.validation";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.validation";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Transforming validation imports from " + OLD_PACKAGE_PREFIX + " to " + NEW_PACKAGE_PREFIX);
        System.out.println("Source directory: " + sourceDirectory);
        
        try {
            transformProject(sourceDirectory);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDirectory) throws IOException {
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist or is not a directory: " + sourceDirectory);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int transformedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Successfully transformed " + transformedFiles + " files");
    }
    
    private static boolean transformFile(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();
            
            boolean wasModified = false;
            
            // Transform imports
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                    String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                    importDecl.setName(new Name(newImportName));
                    wasModified = true;
                    System.out.println("  - Updated import: " + importName + " -> " + newImportName);
                }
            }
            
            // Transform fully qualified names in code
            FullyQualifiedNameTransformer transformer = new FullyQualifiedNameTransformer();
            cu.accept(transformer, null);
            if (transformer.wasModified()) {
                wasModified = true;
            }
            
            if (wasModified) {
                Files.writeString(javaFile, cu.toString());
                System.out.println("✓ Transformed: " + javaFile.getFileName());
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Visitor that transforms fully qualified names in the code
     */
    private static class FullyQualifiedNameTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(Name name, Void arg) {
            String nameAsString = name.asString();
            if (nameAsString.startsWith(OLD_PACKAGE_PREFIX)) {
                String newName = nameAsString.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                name.setIdentifier(newName);
                modified = true;
                System.out.println("  - Updated FQN: " + nameAsString + " -> " + newName);
            }
            return super.visit(name, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}