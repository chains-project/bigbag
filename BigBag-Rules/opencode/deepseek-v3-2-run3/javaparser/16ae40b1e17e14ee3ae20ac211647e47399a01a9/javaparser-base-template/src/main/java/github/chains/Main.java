package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    // Configuration for the transformation
    private static final String OLD_PACKAGE = "net.lingala.zip4j.core";
    private static final String NEW_PACKAGE = "net.lingala.zip4j";
    private static final String CLASS_NAME = "ZipFile";
    
    // Old fully qualified class name
    private static final String OLD_FULL_CLASS_NAME = OLD_PACKAGE + "." + CLASS_NAME;
    // New fully qualified class name  
    private static final String NEW_FULL_CLASS_NAME = NEW_PACKAGE + "." + CLASS_NAME;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming source files in: " + sourceDir);
        System.out.println("Transformation: " + OLD_FULL_CLASS_NAME + " -> " + NEW_FULL_CLASS_NAME);
        
        // Find all Java files
        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        int transformedFiles = 0;
        int totalChanges = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                boolean fileModified = false;
                
                // 1. Transform import statements
                List<ImportDeclaration> imports = cu.getImports();
                for (ImportDeclaration importDecl : imports) {
                    if (importDecl.isAsterisk()) {
                        // Handle wildcard imports like: import net.lingala.zip4j.core.*;
                        String importName = importDecl.getNameAsString();
                        if (importName.equals(OLD_PACKAGE)) {
                            importDecl.setName(NEW_PACKAGE);
                            fileModified = true;
                            System.out.println("  Transformed wildcard import in " + javaFile + ": " + importName + ".* -> " + NEW_PACKAGE + ".*");
                        }
                    } else {
                        // Handle specific class imports like: import net.lingala.zip4j.core.ZipFile;
                        String importName = importDecl.getNameAsString();
                        if (importName.equals(OLD_FULL_CLASS_NAME)) {
                            importDecl.setName(NEW_FULL_CLASS_NAME);
                            fileModified = true;
                            System.out.println("  Transformed import in " + javaFile + ": " + importName + " -> " + NEW_FULL_CLASS_NAME);
                        } else if (importName.startsWith(OLD_PACKAGE + ".") && importName.endsWith("." + CLASS_NAME)) {
                            // Handle case where class name might be different but same package structure
                            // This catches any class in old package that might have moved
                            String newImportName = NEW_PACKAGE + "." + CLASS_NAME;
                            importDecl.setName(newImportName);
                            fileModified = true;
                            System.out.println("  Transformed import in " + javaFile + ": " + importName + " -> " + newImportName);
                        }
                    }
                }
                
                // 2. Transform fully-qualified type references in code
                // This handles cases like: net.lingala.zip4j.core.ZipFile zip = new net.lingala.zip4j.core.ZipFile(...);
                boolean[] fileModifiedRef = new boolean[]{fileModified};
                cu.walk(Name.class, name -> {
                    String nameStr = name.asString();
                    if (nameStr.equals(OLD_FULL_CLASS_NAME)) {
                        name.setIdentifier(NEW_FULL_CLASS_NAME);
                        fileModifiedRef[0] = true;
                    } else if (nameStr.startsWith(OLD_PACKAGE) && nameStr.endsWith("." + CLASS_NAME)) {
                        String newName = NEW_PACKAGE + "." + CLASS_NAME;
                        name.setIdentifier(newName);
                        fileModifiedRef[0] = true;
                    }
                });
                fileModified = fileModifiedRef[0];
                
                // 3. Transform ClassOrInterfaceType references (type declarations, extends, implements, etc.)
                // This is more complex since we need to handle qualified names in type references
                // For now, we'll rely on the import transformation and fully-qualified name transformation
                // Most usage will be either imported or fully-qualified in code
                
                if (fileModified) {
                    // Write the transformed file
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                    String transformedCode = printer.print(cu);
                    Files.write(javaFile, transformedCode.getBytes());
                    
                    transformedFiles++;
                    // Count changes (simplified - each file with changes counts as 1)
                    totalChanges++;
                    System.out.println("  Updated: " + javaFile);
                }
                
            } catch (IOException e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Unexpected error processing file " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Total changes: " + totalChanges);
        
        if (transformedFiles > 0) {
            System.out.println("\nSummary of transformation:");
            System.out.println("  Old: " + OLD_FULL_CLASS_NAME);
            System.out.println("  New: " + NEW_FULL_CLASS_NAME);
            System.out.println("  This transformation fixes the package relocation breaking change in zip4j 2.11.1");
        }
    }
}