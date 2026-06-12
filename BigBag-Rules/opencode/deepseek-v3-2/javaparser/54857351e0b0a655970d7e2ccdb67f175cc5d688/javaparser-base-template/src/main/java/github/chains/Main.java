package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Generic JavaParser transformation for fixing package relocation breaking changes.
 * 
 * This tool handles the pattern where a class moves from one package to another
 * in a dependency update (e.g., net.lingala.zip4j.core.ZipFile -> net.lingala.zip4j.ZipFile).
 * 
 * The transformation:
 * 1. Updates import statements
 * 2. Updates fully qualified type references in code
 * 3. Updates object creation expressions
 * 
 * Usage: java -jar javaparser.jar <sourceDir> <oldFullyQualifiedClass> <newFullyQualifiedClass>
 * Example: java -jar javaparser.jar /project/src net.lingala.zip4j.core.ZipFile net.lingala.zip4j.ZipFile
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java -jar javaparser.jar <sourceDir> <oldFullyQualifiedClass> <newFullyQualifiedClass>");
            System.err.println("Example: java -jar javaparser.jar /path/to/src net.lingala.zip4j.core.ZipFile net.lingala.zip4j.ZipFile");
            System.err.println("Example: java -jar javaparser.jar /path/to/src old.pkg.ClassName new.pkg.ClassName");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldFullyQualifiedClass = args[1];
        String newFullyQualifiedClass = args[2];
        
        // Extract package names from fully qualified class names
        String oldPackage = extractPackage(oldFullyQualifiedClass);
        String newPackage = extractPackage(newFullyQualifiedClass);
        String className = extractClassName(oldFullyQualifiedClass);
        
        // Verify the class name is the same (only package changed)
        if (!className.equals(extractClassName(newFullyQualifiedClass))) {
            System.err.println("Error: Class names must be the same for package relocation.");
            System.err.println("Old: " + className + ", New: " + extractClassName(newFullyQualifiedClass));
            System.exit(1);
        }
        
        System.out.println("Transforming: " + oldFullyQualifiedClass + " -> " + newFullyQualifiedClass);
        System.out.println("Old package: " + oldPackage);
        System.out.println("New package: " + newPackage);
        System.out.println("Class name: " + className);
        
        try {
            processDirectory(sourceDir, oldPackage, newPackage, className);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static String extractPackage(String fullyQualifiedClass) {
        int lastDot = fullyQualifiedClass.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return fullyQualifiedClass.substring(0, lastDot);
    }
    
    private static String extractClassName(String fullyQualifiedClass) {
        int lastDot = fullyQualifiedClass.lastIndexOf('.');
        if (lastDot == -1) {
            return fullyQualifiedClass;
        }
        return fullyQualifiedClass.substring(lastDot + 1);
    }
    
    private static void processDirectory(String sourceDir, String oldPackage, String newPackage, String className) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        JavaParser parser = new JavaParser();
        
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> processFile(path, oldPackage, newPackage, className, parser));
    }
    
    private static void processFile(Path filePath, String oldPackage, String newPackage, String className, JavaParser parser) {
        try {
            String content = Files.readString(filePath);
            Optional<CompilationUnit> cuOpt = parser.parse(content).getResult();
            
            if (cuOpt.isPresent()) {
                CompilationUnit cu = cuOpt.get();
                boolean[] modified = {false}; // Use array for lambda access
                
                String oldFullyQualified = oldPackage + "." + className;
                String newFullyQualified = newPackage + "." + className;
                
                // Process imports
                List<ImportDeclaration> imports = cu.getImports();
                for (ImportDeclaration importDecl : imports) {
                    String importName = importDecl.getNameAsString();
                    
                    // Check if this import matches the old fully qualified class
                    if (importName.equals(oldFullyQualified)) {
                        // Update to new fully qualified class
                        importDecl.setName(newFullyQualified);
                        modified[0] = true;
                        System.out.println("Updated import in " + filePath + ": " + importName + " -> " + newFullyQualified);
                    }
                    // Also check for star imports of the old package
                    else if (importDecl.isAsterisk() && importName.equals(oldPackage)) {
                        // Change star import to new package
                        importDecl.setName(newPackage);
                        modified[0] = true;
                        System.out.println("Updated star import in " + filePath + ": " + importName + " -> " + newPackage);
                    }
                }
                
                // Process type references throughout the AST
                // This includes variable types, method return types, parameter types, etc.
                cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
                    String typeName = type.getNameAsString();
                    
                    // Check for fully qualified references
                    if (typeName.equals(oldFullyQualified)) {
                        type.setName(newFullyQualified);
                        modified[0] = true;
                        System.out.println("Updated fully qualified type in " + filePath + ": " + typeName + " -> " + newFullyQualified);
                    }
                });
                
                // Update object creation expressions: new OldClass() or new old.pkg.OldClass()
                cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
                    // expr.getType() returns ClassOrInterfaceType directly
                    ClassOrInterfaceType type = expr.getType();
                    String typeName = type.getNameAsString();
                    
                    if (typeName.equals(oldFullyQualified)) {
                        type.setName(newFullyQualified);
                        modified[0] = true;
                        System.out.println("Updated fully qualified object creation in " + filePath + ": new " + typeName + "() -> new " + newFullyQualified + "()");
                    }
                });
                
                if (modified[0]) {
                    // Write the modified file
                    PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
                    PrettyPrinter printer = new PrettyPrinter(config);
                    String updatedContent = printer.print(cu);
                    Files.writeString(filePath, updatedContent);
                    System.out.println("Successfully updated file: " + filePath);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading/writing file " + filePath + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}