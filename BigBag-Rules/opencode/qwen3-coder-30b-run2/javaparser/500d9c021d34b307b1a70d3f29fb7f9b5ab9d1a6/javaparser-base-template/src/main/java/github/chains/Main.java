package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // Default path - this can be overridden by command line arguments
        String sourceDir = "/workspace/jakartaee-mvc-sample";
        
        if (args.length > 0) {
            sourceDir = args[0];
        }
        
        // Process all Java files in the directory recursively
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation to imports
            transformImports(cu);
            
            // Save the modified file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
                writer.write(cu.toString());
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Transform javax.mvc imports to jakarta.mvc imports
     */
    private static void transformImports(CompilationUnit cu) {
        // List of packages to migrate
        List<String> oldPackages = Arrays.asList(
                "javax.mvc",
                "javax.mvc.binding",
                "javax.mvc.event",
                "javax.mvc.locale",
                "javax.mvc.security"
        );
        
        List<String> newPackages = Arrays.asList(
                "jakarta.mvc",
                "jakarta.mvc.binding",
                "jakarta.mvc.event",
                "jakarta.mvc.locale",
                "jakarta.mvc.security"
        );
        
        // Iterate through all imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importedName = importDecl.getNameAsString();
            
            for (int i = 0; i < oldPackages.size(); i++) {
                String oldPackage = oldPackages.get(i);
                String newPackage = newPackages.get(i);
                
                if (importedName.startsWith(oldPackage + ".")) {
                    // Replace the import
                    String newName = importedName.replaceFirst(oldPackage, newPackage);
                    importDecl.setName(newName);
                    break;
                } else if (importedName.equals(oldPackage)) {
                    // Handle the case where the whole package is imported
                    String newName = importedName.replaceFirst(oldPackage, newPackage);
                    importDecl.setName(newName);
                    break;
                }
            }
        }
    }
}