package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.Node;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration: List of classes that have been removed in the new API version
    private static final List<String> REMOVED_CLASSES = List.of(
        "jakarta.servlet.http.HttpSessionContext"
        // Add other removed classes here as needed
    );
    
    // Configuration: For methods returning removed types, what should we change them to?
    // Use "Object" as a generic fallback, or specify custom mappings
    private static final String FALLBACK_RETURN_TYPE = "Object";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Applies transformations to fix breaking API changes in Jakarta Servlet API 6.0.0");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles;
            try {
                javaFiles = findJavaFiles(Paths.get(sourceDir));
            } catch (IOException e) {
                System.err.println("Error finding Java files: " + e.getMessage());
                System.exit(1);
                return;
            }
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        return Files.walk(startDir)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
    }
    
    private static boolean processFile(Path javaFile) throws FileNotFoundException, IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        boolean[] modified = new boolean[]{false};
        
        // Visitor to remove imports of removed classes
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ImportDeclaration importDecl, Void arg) {
                String importName = importDecl.getNameAsString();
                
                // Check if this import is for a removed class
                for (String removedClass : REMOVED_CLASSES) {
                    if (importName.equals(removedClass) || 
                        importName.startsWith(removedClass + ".")) {
                        modified[0] = true;
                        return null; // Remove the import
                    }
                }
                
                return (Node) super.visit(importDecl, arg);
            }
            
            @Override
            public Node visit(MethodDeclaration methodDecl, Void arg) {
                // Check if return type is a removed class
                Type returnType = methodDecl.getType();
                if (returnType instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType classType = (ClassOrInterfaceType) returnType;
                    String typeName = classType.getNameAsString();
                    String typeNameWithScope = buildFullyQualifiedName(classType);
                    
                    for (String removedClass : REMOVED_CLASSES) {
                        String simpleName = getSimpleName(removedClass);
                        if (typeName.equals(simpleName) || 
                            typeNameWithScope != null && typeNameWithScope.equals(removedClass)) {
                            // Change return type to fallback type
                            modified[0] = true;
                            methodDecl.setType(FALLBACK_RETURN_TYPE);
                            
                            // Remove @Override annotation since the method signature changed
                            // and might not match parent interface anymore
                            methodDecl.getAnnotationByName("Override").ifPresent(anno -> {
                                methodDecl.remove(anno);
                            });
                            break;
                        }
                    }
                }
                
                // Also check parameter types
                NodeList<Parameter> parameters = methodDecl.getParameters();
                boolean changedParamType = false;
                for (Parameter param : parameters) {
                    Type paramType = param.getType();
                    if (paramType instanceof ClassOrInterfaceType) {
                        ClassOrInterfaceType classType = (ClassOrInterfaceType) paramType;
                        String typeName = classType.getNameAsString();
                        String typeNameWithScope = buildFullyQualifiedName(classType);
                        
                        for (String removedClass : REMOVED_CLASSES) {
                            String simpleName = getSimpleName(removedClass);
                            if (typeName.equals(simpleName) || 
                                typeNameWithScope != null && typeNameWithScope.equals(removedClass)) {
                                // Change parameter type to fallback type
                                modified[0] = true;
                                changedParamType = true;
                                param.setType(FALLBACK_RETURN_TYPE);
                                break;
                            }
                        }
                    }
                }
                
                // If we changed any parameter type, also remove @Override annotation
                if (changedParamType) {
                    methodDecl.getAnnotationByName("Override").ifPresent(anno -> {
                        methodDecl.remove(anno);
                    });
                }
                
                return (Node) super.visit(methodDecl, arg);
            }
            
            @Override
            public Node visit(ClassOrInterfaceType type, Void arg) {
                // Handle type references in fields, variables, etc.
                String typeName = type.getNameAsString();
                String typeNameWithScope = buildFullyQualifiedName(type);
                
                for (String removedClass : REMOVED_CLASSES) {
                    String simpleName = getSimpleName(removedClass);
                    if (typeName.equals(simpleName) || 
                        typeNameWithScope != null && typeNameWithScope.equals(removedClass)) {
                        // Change type to fallback type
                        modified[0] = true;
                        type.setName(FALLBACK_RETURN_TYPE);
                        break;
                    }
                }
                
                return (Node) super.visit(type, arg);
            }
        }, null);
        
        if (modified[0]) {
            // Write back the modified file
            PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
            config.setPrintComments(true);
            config.setEndOfLineCharacter("\n");
            
            String newContent = new PrettyPrinter(config).print(cu);
            try {
                Files.writeString(javaFile, newContent);
                System.out.println("Modified: " + javaFile);
                return true;
            } catch (IOException e) {
                System.err.println("Error writing file " + javaFile + ": " + e.getMessage());
                return false;
            }
        }
        
        return false;
    }
    
    private static String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }
    
    private static String buildFullyQualifiedName(ClassOrInterfaceType type) {
        if (type.getScope().isPresent()) {
            return buildFullyQualifiedName(type.getScope().get()) + "." + type.getNameAsString();
        }
        return type.getNameAsString();
    }
}