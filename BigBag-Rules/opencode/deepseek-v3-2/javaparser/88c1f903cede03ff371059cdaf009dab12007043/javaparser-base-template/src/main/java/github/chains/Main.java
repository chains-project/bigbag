package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    /**
     * Generic transformation rule for fixing package relocation breaking changes.
     * 
     * This rule handles cases where a class has been moved from one package to another.
     * For example: net.lingala.zip4j.core.ZipFile -> net.lingala.zip4j.ZipFile
     * 
     * The transformation handles:
     * 1. Import statement updates
     * 
     * Parameters:
     * - oldFullyQualifiedName: The old fully qualified class name (e.g., "net.lingala.zip4j.core.ZipFile")
     * - newFullyQualifiedName: The new fully qualified class name (e.g., "net.lingala.zip4j.ZipFile")
     * - sourceDirectory: The directory containing Java source files to transform
     */
    public static void fixPackageRelocation(String oldFullyQualifiedName, 
                                          String newFullyQualifiedName, 
                                          Path sourceDirectory) throws IOException {
        
        // Parse old and new names to extract package and class name
        String oldPackage = extractPackageName(oldFullyQualifiedName);
        String newPackage = extractPackageName(newFullyQualifiedName);
        String className = extractClassName(oldFullyQualifiedName);
        
        System.out.println("Transformation Configuration:");
        System.out.println("  Old FQN: " + oldFullyQualifiedName);
        System.out.println("  New FQN: " + newFullyQualifiedName);
        System.out.println("  Old Package: " + oldPackage);
        System.out.println("  New Package: " + newPackage);
        System.out.println("  Class Name: " + className);
        System.out.println("  Source Directory: " + sourceDirectory);
        
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("\nFound " + javaFiles.size() + " Java files to process");
            
            int filesModified = 0;
            for (Path javaFile : javaFiles) {
                System.out.println("\nProcessing: " + javaFile);
                if (processFile(javaFile, oldFullyQualifiedName, newFullyQualifiedName, 
                              oldPackage, newPackage, className)) {
                    filesModified++;
                }
            }
            
            System.out.println("\nSummary: Modified " + filesModified + " out of " + javaFiles.size() + " files");
        }
    }
    
    private static boolean processFile(Path javaFile, 
                                     String oldFullyQualifiedName, String newFullyQualifiedName,
                                     String oldPackage, String newPackage, String className) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            
            if (cu == null) {
                System.out.println("  Could not parse file");
                return false;
            }
            
            boolean modified = false;
            
            // 1. Update import statements
            List<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                String importedName = importDecl.getNameAsString();
                
                if (importedName.equals(oldFullyQualifiedName)) {
                    // Direct import of the old fully qualified class
                    importDecl.setName(newFullyQualifiedName);
                    modified = true;
                    System.out.println("  ✓ Updated import: " + importedName + " -> " + newFullyQualifiedName);
                } else if (importedName.equals(oldPackage + ".*")) {
                    // Wildcard import of the old package
                    // Note: We can't automatically update wildcard imports without knowing
                    // if other classes from the old package are also used
                    // We'll leave wildcard imports as-is since they might still work
                    // if classes weren't moved, or we'd need a different transformation
                    System.out.println("  ⚠ Wildcard import of old package: " + importedName);
                    System.out.println("    Consider updating to: " + newPackage + ".* if all classes were moved");
                }
            }
            
            // Save the file if modified
            if (modified) {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String updatedContent = printer.print(cu);
                
                Files.write(javaFile, updatedContent.getBytes());
                System.out.println("  ✓ File updated successfully");
                return true;
            } else {
                System.out.println("  No changes needed");
            }
            
        } catch (Exception e) {
            System.err.println("  ✗ Error processing file: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private static String extractPackageName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            return fullyQualifiedName.substring(0, lastDot);
        }
        return "";
    }
    
    private static String extractClassName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            return fullyQualifiedName.substring(lastDot + 1);
        }
        return fullyQualifiedName;
    }
    
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java -jar javaparser.jar <oldFullyQualifiedName> <newFullyQualifiedName> <sourceDirectory>");
            System.out.println("Example: java -jar javaparser.jar net.lingala.zip4j.core.ZipFile net.lingala.zip4j.ZipFile /path/to/src");
            System.out.println("\nThis transformation fixes package relocation breaking changes in Java projects.");
            System.exit(1);
        }
        
        String oldFullyQualifiedName = args[0];
        String newFullyQualifiedName = args[1];
        Path sourceDirectory = Paths.get(args[2]);
        
        if (!Files.exists(sourceDirectory) || !Files.isDirectory(sourceDirectory)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDirectory);
            System.exit(1);
        }
        
        try {
            fixPackageRelocation(oldFullyQualifiedName, newFullyQualifiedName, sourceDirectory);
            System.out.println("\nTransformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}