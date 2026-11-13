package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Classes that moved from com.github.javaparser.printer to com.github.javaparser.printer.configuration
    private static final String[] MOVED_CLASSES = {
        "PrettyPrinterConfiguration",
        "PrinterConfiguration",
        "DefaultPrinterConfiguration",
        "DefaultConfigurationOption",
        "Indentation"
    };
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        try {
            List<Path> javaFiles = new ArrayList<>();
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    JavaParser parser = new JavaParser();
                    CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                    
                    if (cu == null) {
                        System.err.println("Warning: Could not parse " + javaFile);
                        continue;
                    }
                    
                    boolean fileModified = false;
                    int fileTransformations = 0;
                    
                    // Transform imports
                    for (ImportDeclaration importDecl : cu.getImports()) {
                        String importName = importDecl.getNameAsString();
                        
                        for (String className : MOVED_CLASSES) {
                            String oldImport = "com.github.javaparser.printer." + className;
                            String newImport = "com.github.javaparser.printer.configuration." + className;
                            
                            if (importName.equals(oldImport)) {
                                importDecl.setName(newImport);
                                fileModified = true;
                                fileTransformations++;
                                System.out.println("  Updated import: " + oldImport + " -> " + newImport);
                            }
                        }
                    }
                    
                    // Transform fully-qualified type references in the code
                    cu.walk(Node.class, node -> {
                        // Check if node is a Name that might be a fully-qualified type reference
                        if (node instanceof Name) {
                            Name name = (Name) node;
                            String nameStr = name.asString();
                            
                            for (String className : MOVED_CLASSES) {
                                String oldFQN = "com.github.javaparser.printer." + className;
                                if (nameStr.equals(oldFQN)) {
                                    name.setIdentifier("com.github.javaparser.printer.configuration." + className);
                                    return;
                                }
                            }
                        }
                        
                        // Also check ClassOrInterfaceType nodes
                        if (node instanceof ClassOrInterfaceType) {
                            ClassOrInterfaceType type = (ClassOrInterfaceType) node;
                            
                            // Check if it has a scope (e.g., com.github.javaparser.printer.PrettyPrinterConfiguration)
                            if (type.getScope().isPresent()) {
                                String scope = type.getScope().get().asString();
                                String name = type.getNameAsString();
                                
                                for (String className : MOVED_CLASSES) {
                                    if (name.equals(className) && scope.equals("com.github.javaparser.printer")) {
                                        // Create new name with updated scope
                                        type.setName("com.github.javaparser.printer.configuration." + className);
                                        return;
                                    }
                                }
                            }
                        }
                    });
                    
                    if (fileModified) {
                        // Write back the transformed file
                        Path outputFile = javaFile;
                        Files.write(outputFile, cu.toString().getBytes());
                        transformedFiles++;
                        totalTransformations += fileTransformations;
                        System.out.println("Transformed " + javaFile + " (" + fileTransformations + " changes)");
                    }
                    
                } catch (IOException e) {
                    System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Unexpected error processing file " + javaFile + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("\nTransformation complete!");
            System.out.println("Files transformed: " + transformedFiles + " of " + javaFiles.size());
            System.out.println("Total transformations applied: " + totalTransformations);
            
        } catch (IOException e) {
            System.err.println("Error walking file tree: " + e.getMessage());
            System.exit(1);
        }
    }
}