package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration for the breaking change
    // These can be parameterized for different breaking changes
    private static final String OLD_PACKAGE = "com.google.api.services.translate.model";
    private static final String NEW_PACKAGE = "com.google.api.services.translate.v3.model";
    
    // Map of old class names to new class names
    private static final Map<String, String> CLASS_NAME_MAPPINGS = new HashMap<>();
    static {
        CLASS_NAME_MAPPINGS.put("TranslationsResource", "Translation");
        CLASS_NAME_MAPPINGS.put("LanguagesResource", "SupportedLanguage");
        CLASS_NAME_MAPPINGS.put("DetectionsResourceItems", "DetectedLanguage");
    }
    
    // Map of old package prefixes to new package prefixes (for broader package changes)
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    static {
        PACKAGE_MAPPINGS.put("com.google.api.services.translate", "com.google.api.services.translate.v3");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            Path startDir = Paths.get(sourceDir);
            if (!Files.exists(startDir) || !Files.isDirectory(startDir)) {
                System.err.println("Error: Source directory does not exist: " + sourceDir);
                System.exit(1);
            }
            
            Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".java")) {
                        processJavaFile(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    System.err.println("Error visiting file: " + file + " - " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
            
            System.out.println("Transformation completed successfully!");
            
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            System.out.println("Processing: " + javaFile);
            
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse: " + javaFile));
            
            boolean modified = false;
            
            // 1. Update import declarations
            modified = updateImports(cu) || modified;
            
            // 2. Update type references in the code
            modified = updateTypeReferences(cu) || modified;
            
            // 3. Update generic type parameters
            modified = updateGenericTypes(cu) || modified;
            
            if (modified) {
                // Write the modified file back
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
                String newContent = printer.print(cu);
                
                try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                    writer.write(newContent);
                    System.out.println("  Updated: " + javaFile);
                }
            } else {
                System.out.println("  No changes needed: " + javaFile);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean updateImports(CompilationUnit cu) {
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            
            // First, check for broader package mappings
            for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
                String oldPkg = entry.getKey();
                String newPkg = entry.getValue();
                
                if (importName.startsWith(oldPkg)) {
                    // Extract the class name from the import
                    String className = importName.substring(importName.lastIndexOf('.') + 1);
                    
                    // Check if this class needs to be renamed
                    if (CLASS_NAME_MAPPINGS.containsKey(className)) {
                        String newClassName = CLASS_NAME_MAPPINGS.get(className);
                        String newImportName = newPkg + ".model." + newClassName;
                        
                        importDecl.setName(newImportName);
                        modified = true;
                        System.out.println("    Updated import: " + importName + " -> " + newImportName);
                    } else {
                        // Just update the package
                        String newImportName = importName.replace(oldPkg, newPkg);
                        importDecl.setName(newImportName);
                        modified = true;
                        System.out.println("    Updated import package: " + importName + " -> " + newImportName);
                    }
                    break;
                }
            }
            
            // Also check for specific old package
            if (importName.startsWith(OLD_PACKAGE)) {
                // Extract the class name from the import
                String className = importName.substring(importName.lastIndexOf('.') + 1);
                
                // Check if this class needs to be renamed
                if (CLASS_NAME_MAPPINGS.containsKey(className)) {
                    String newClassName = CLASS_NAME_MAPPINGS.get(className);
                    String newImportName = NEW_PACKAGE + "." + newClassName;
                    
                    importDecl.setName(newImportName);
                    modified = true;
                    System.out.println("    Updated import: " + importName + " -> " + newImportName);
                } else {
                    // Just update the package
                    String newImportName = importName.replace(OLD_PACKAGE, NEW_PACKAGE);
                    importDecl.setName(newImportName);
                    modified = true;
                    System.out.println("    Updated import package: " + importName + " -> " + newImportName);
                }
            }
        }
        
        return modified;
    }
    
    private static boolean updateTypeReferences(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        // Visit all type references in the compilation unit
        cu.accept(new com.github.javaparser.ast.visitor.ModifierVisitor<Void>() {
            @Override
            public com.github.javaparser.ast.Node visit(ClassOrInterfaceType type, Void arg) {
                ClassOrInterfaceType visited = (ClassOrInterfaceType) super.visit(type, arg);
                
                // Check if this type name matches one of our old class names
                String typeName = visited.getNameAsString();
                if (CLASS_NAME_MAPPINGS.containsKey(typeName)) {
                    String newTypeName = CLASS_NAME_MAPPINGS.get(typeName);
                    visited.setName(newTypeName);
                    modified[0] = true;
                    System.out.println("    Updated type reference: " + typeName + " -> " + newTypeName);
                }
                
                return visited;
            }
        }, null);
        
        return modified[0];
    }
    
    private static boolean updateGenericTypes(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        // This handles cases like List<TranslationsResource>, List<List<DetectionsResourceItems>>, etc.
        cu.accept(new com.github.javaparser.ast.visitor.ModifierVisitor<Void>() {
            @Override
            public com.github.javaparser.ast.Node visit(ClassOrInterfaceType type, Void arg) {
                ClassOrInterfaceType visited = (ClassOrInterfaceType) super.visit(type, arg);
                
                // Check type arguments recursively
                if (visited.getTypeArguments().isPresent()) {
                    NodeList<Type> typeArgs = visited.getTypeArguments().get();
                    for (int i = 0; i < typeArgs.size(); i++) {
                        Type typeArg = typeArgs.get(i);
                        
                        if (typeArg.isClassOrInterfaceType()) {
                            ClassOrInterfaceType typeArgClass = typeArg.asClassOrInterfaceType();
                            String typeName = typeArgClass.getNameAsString();
                            
                            if (CLASS_NAME_MAPPINGS.containsKey(typeName)) {
                                String newTypeName = CLASS_NAME_MAPPINGS.get(typeName);
                                typeArgClass.setName(newTypeName);
                                modified[0] = true;
                                System.out.println("    Updated generic type: " + typeName + " -> " + newTypeName);
                            }
                        }
                    }
                }
                
                return visited;
            }
        }, null);
        
        return modified[0];
    }
}