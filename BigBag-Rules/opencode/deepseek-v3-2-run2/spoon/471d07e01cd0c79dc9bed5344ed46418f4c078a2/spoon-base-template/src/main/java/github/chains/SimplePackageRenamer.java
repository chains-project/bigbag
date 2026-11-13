package github.chains;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SimplePackageRenamer {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: java SimplePackageRenamer <source-dir> <output-dir> <old-package> <new-package>");
            System.err.println("Example: java SimplePackageRenamer /path/to/src /path/out \\");
            System.err.println("  \"com.google.api.services.cloudresourcemanager.model\" \\");
            System.err.println("  \"com.google.api.services.cloudresourcemanager.v3.model\"");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String oldPackage = args[2];
        String newPackage = args[3];
        
        System.out.println("Starting package rename transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Package rename: " + oldPackage + " -> " + newPackage);
        
        // Create output directory
        Files.createDirectories(Paths.get(outputDir));
        
        // Walk through source files
        Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(sourceFile -> {
                try {
                    transformFile(sourceFile, Paths.get(outputDir), oldPackage, newPackage);
                } catch (IOException e) {
                    System.err.println("Error processing " + sourceFile + ": " + e.getMessage());
                }
            });
        
        System.out.println("Transformation completed!");
    }
    
    private static void transformFile(Path sourceFile, Path outputDir, String oldPackage, String newPackage) throws IOException {
        String content = Files.readString(sourceFile);
        String transformed = transformContent(content, oldPackage, newPackage);
        
        // Create output path preserving directory structure
        Path relativePath = Paths.get(".").relativize(sourceFile);
        Path outputFile = outputDir.resolve(relativePath);
        
        // Create parent directories if needed
        Files.createDirectories(outputFile.getParent());
        
        // Write transformed content
        Files.writeString(outputFile, transformed);
        
        System.out.println("Processed: " + sourceFile);
    }
    
    private static String transformContent(String content, String oldPackage, String newPackage) {
        // Transform import statements
        String importPattern = "import\\s+" + Pattern.quote(oldPackage) + "\\.([^;]+);";
        String transformed = content.replaceAll(importPattern, "import " + newPackage + ".$1;");
        
        // Transform fully-qualified type names in code
        String typePattern = "\\b" + Pattern.quote(oldPackage) + "\\.([A-Za-z_$][A-Za-z0-9_$]*)";
        transformed = transformed.replaceAll(typePattern, newPackage + ".$1");
        
        return transformed;
    }
}