package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration parameters - these should be changed for different dependency upgrades
    private static final String DEPENDENCY_CLASS_NAME = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String METHOD_NAME = "readValue";
    private static final String NEW_EXCEPTION_TYPE_1 = "com.fasterxml.jackson.core.JsonProcessingException";
    private static final String NEW_EXCEPTION_TYPE_2 = "com.fasterxml.jackson.databind.JsonMappingException";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        boolean modified = false;
        
        // Find all methods that call the problematic API
        List<MethodDeclaration> affectedMethods = cu.findAll(MethodDeclaration.class, 
            method -> containsProblematicCall(method));
        
        for (MethodDeclaration method : affectedMethods) {
            if (addExceptionToMethod(method)) {
                modified = true;
            }
        }
        
        if (modified) {
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Modified: " + filePath);
        }
        
        return modified;
    }
    
    private static boolean containsProblematicCall(MethodDeclaration method) {
        return method.findAll(MethodCallExpr.class, call -> 
            call.getNameAsString().equals(METHOD_NAME) &&
            call.getScope().isPresent() &&
            isObjectMapperType(call.getScope().get())
        ).size() > 0;
    }
    
    private static boolean isObjectMapperType(Node node) {
        // This is a simplified check - in a real implementation, you would
        // need to resolve the actual type using symbol solver
        String nodeString = node.toString();
        return nodeString.contains("objectMapper") || nodeString.contains("ObjectMapper");
    }
    
    private static boolean addExceptionToMethod(MethodDeclaration method) {
        // Check if the method already declares the new exceptions
        boolean hasNewException1 = method.getThrownExceptions().stream()
            .anyMatch(exception -> exception.toString().contains("JsonProcessingException"));
        boolean hasNewException2 = method.getThrownExceptions().stream()
            .anyMatch(exception -> exception.toString().contains("JsonMappingException"));
        
        if (hasNewException1 && hasNewException2) {
            return false;
        }
        
        // Add the new exceptions to the throws clause
        if (!hasNewException1) {
            ClassOrInterfaceType newException1 = new ClassOrInterfaceType(null, NEW_EXCEPTION_TYPE_1);
            method.addThrownException(newException1);
        }
        if (!hasNewException2) {
            ClassOrInterfaceType newException2 = new ClassOrInterfaceType(null, NEW_EXCEPTION_TYPE_2);
            method.addThrownException(newException2);
        }
        return true;
    }
}