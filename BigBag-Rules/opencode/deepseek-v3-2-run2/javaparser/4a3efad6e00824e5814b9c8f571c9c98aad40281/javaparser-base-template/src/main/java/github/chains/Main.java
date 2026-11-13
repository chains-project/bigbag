package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for package relocation breaking changes.
 * This rule can be applied to any project affected by a class being moved
 * from one package to another.
 */
public class Main {
    
    // Configuration parameters - these should be changed for different breaking changes
    private static final String OLD_PACKAGE = "eu.europa.esig.dss.pades.CertificationPermission";
    private static final String NEW_PACKAGE = "eu.europa.esig.dss.enumerations.CertificationPermission";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This transformation will update:");
            System.err.println("  " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Transformation: " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (applyTransformation(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Recursively finds all Java files in the given directory
     */
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        return Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    /**
     * Applies the transformation to a single Java file
     * @return true if the file was modified, false otherwise
     */
    private static boolean applyTransformation(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        
        // Parse the Java file
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        // Check each import statement
        for (int i = 0; i < imports.size(); i++) {
            ImportDeclaration importDecl = imports.get(i);
            String importName = importDecl.getNameAsString();
            
            // Check if this import matches our old package
            if (importName.equals(OLD_PACKAGE)) {
                System.out.println("Updating import in " + javaFile + ": " + importName + " -> " + NEW_PACKAGE);
                
                // Create new import with the new package
                ImportDeclaration newImport = new ImportDeclaration(
                    new Name(NEW_PACKAGE),
                    importDecl.isStatic(),
                    importDecl.isAsterisk()
                );
                
                // Replace the old import with the new one
                imports.set(i, newImport);
                modified = true;
            }
        }
        
        // Also check for fully qualified names in the code
        // This handles cases where the class is used without an import
        final boolean[] codeModified = {false};
        cu.walk(Name.class, name -> {
            if (name.asString().equals(OLD_PACKAGE)) {
                name.replace(new Name(NEW_PACKAGE));
                codeModified[0] = true;
            }
        });
        
        if (codeModified[0]) {
            modified = true;
        }
        
        // If the file was modified, write it back
        if (modified) {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String newContent = printer.print(cu);
            
            // Also perform simple string replacement for fully-qualified names
            // This handles cases where the AST parsing doesn't catch all instances
            if (newContent.contains(OLD_PACKAGE)) {
                newContent = newContent.replace(OLD_PACKAGE, NEW_PACKAGE);
                System.out.println("Updated fully-qualified reference in " + javaFile);
            }
            
            Files.writeString(javaFile, newContent);
            return true;
        }
        
        return false;
    }
}