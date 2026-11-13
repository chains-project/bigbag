package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtVisitor;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java -jar spoon-transformer.jar <sourceDir> <outputDir> <oldPackage> <newPackage>");
            System.err.println("Example: java -jar spoon-transformer.jar ./src ./fixed org.mockito.runners org.mockito.junit");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String oldPackage = args[2];
        String newPackage = args[3];
        
        System.out.println("Applying transformation: " + oldPackage + " -> " + newPackage);
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            // Create a simpler approach: process Java files directly
            processJavaFiles(sourceDir, outputDir, oldPackage, newPackage);
            
            System.out.println("\nTransformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFiles(String sourceDir, String outputDir, String oldPackage, String newPackage) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path outputPath = Paths.get(outputDir);
        
        if (!Files.exists(sourcePath)) {
            throw new IOException("Source directory does not exist: " + sourceDir);
        }
        
        // Find all Java files
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFile -> {
                try {
                    processJavaFile(javaFile, sourcePath, outputPath, oldPackage, newPackage);
                } catch (IOException e) {
                    System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                }
            });
    }
    
    private static void processJavaFile(Path javaFile, Path sourceRoot, Path outputRoot, 
                                       String oldPackage, String newPackage) throws IOException {
        String content = Files.readString(javaFile);
        String originalContent = content;
        
        // Pattern to match imports
        Pattern importPattern = Pattern.compile("import\\s+" + Pattern.quote(oldPackage) + "\\.([^;]+);");
        
        // Replace imports
        content = content.replaceAll("import\\s+" + Pattern.quote(oldPackage) + "\\.", 
                                     "import " + newPackage + ".");
        
        // Replace fully qualified references in code
        content = content.replaceAll("\\b" + Pattern.quote(oldPackage) + "\\.", 
                                     newPackage + ".");
        
        // Check if content changed
        if (!content.equals(originalContent)) {
            // Create output path
            Path relativePath = sourceRoot.relativize(javaFile);
            Path outputFile = outputRoot.resolve(relativePath);
            
            // Create parent directories if needed
            Files.createDirectories(outputFile.getParent());
            
            // Write modified content
            Files.writeString(outputFile, content);
            
            System.out.println("Modified: " + relativePath);
        }
    }
}