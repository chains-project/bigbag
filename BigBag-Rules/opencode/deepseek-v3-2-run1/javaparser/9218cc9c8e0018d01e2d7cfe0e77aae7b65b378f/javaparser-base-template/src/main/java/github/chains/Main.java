package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming javax.validation to jakarta.validation in: " + sourceDir);
        
        try {
            List<File> javaFiles = findAllJavaFiles(new File(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            int transformedImports = 0;
            int transformedTypes = 0;
            
            for (File javaFile : javaFiles) {
                boolean fileModified = false;
                
                try (FileInputStream fis = new FileInputStream(javaFile)) {
                    CompilationUnit cu = new JavaParser().parse(fis).getResult().orElseThrow();
                    
                    // Transform imports
                    NodeList<ImportDeclaration> imports = cu.getImports();
                    for (ImportDeclaration importDecl : imports) {
                        String importName = importDecl.getNameAsString();
                        if (importName.startsWith("javax.validation")) {
                            String newImportName = importName.replace("javax.validation", "jakarta.validation");
                            importDecl.setName(new Name(newImportName));
                            fileModified = true;
                            transformedImports++;
                            System.out.println("  Import: " + importName + " -> " + newImportName);
                        }
                    }
                    
                    // Transform type references in the code
                    cu.walk(ClassOrInterfaceType.class, type -> {
                        String typeName = type.getNameAsString();
                        if (typeName.startsWith("javax.validation.")) {
                            String newTypeName = typeName.replace("javax.validation.", "jakarta.validation.");
                            type.setName(newTypeName);
                        }
                    });
                    
                    // Transform fully qualified type names
                    cu.walk(Type.class, type -> {
                        String typeAsString = type.asString();
                        if (typeAsString.contains("javax.validation.")) {
                            String newTypeString = typeAsString.replace("javax.validation.", "jakarta.validation.");
                            // Parse the new type and replace
                            new JavaParser().parseType(newTypeString).ifSuccessful(newType -> {
                                type.replace(newType);
                            });
                        }
                    });
                    
                    if (fileModified) {
                        try (FileWriter fw = new FileWriter(javaFile)) {
                            fw.write(cu.toString());
                        }
                        transformedFiles++;
                        System.out.println("Modified: " + javaFile.getPath());
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error processing file: " + javaFile.getPath());
                    e.printStackTrace();
                }
            }
            
            System.out.println("\nTransformation complete:");
            System.out.println("  Files modified: " + transformedFiles);
            System.out.println("  Imports transformed: " + transformedImports);
            System.out.println("  Type references transformed: " + transformedTypes);
            
        } catch (Exception e) {
            System.err.println("Error during transformation:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<File> findAllJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findAllJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
        return javaFiles;
    }
}