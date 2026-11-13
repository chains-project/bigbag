package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - these can be changed for different breaking changes
    private static final String OLD_PARAMETER_TYPE = "SessionImplementor";
    private static final String NEW_PARAMETER_TYPE = "SharedSessionContractImplementor";
    private static final String METHOD_NAME_NULL_SAFE_GET = "nullSafeGet";
    private static final String METHOD_NAME_NULL_SAFE_SET = "nullSafeSet";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This transformation updates Hibernate UserType implementations to use SharedSessionContractImplementor");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDirectory))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            int modifiedFiles = 0;
            int modifiedMethods = 0;
            
            for (Path javaFile : javaFiles) {
                boolean fileModified = processJavaFile(javaFile.toFile());
                if (fileModified) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Transformation completed:");
            System.out.println("  Files modified: " + modifiedFiles);
            System.out.println("  Methods updated: " + modifiedMethods);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean processJavaFile(File javaFile) {
        try (FileInputStream input = new FileInputStream(javaFile)) {
            JavaParser parser = new JavaParser();
            var parseResult = parser.parse(input);
            
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                System.err.println("Failed to parse file: " + javaFile.getName());
                return false;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            LexicalPreservingPrinter.setup(cu);
            
            boolean modified = false;
            
            // Find all method declarations
            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            
            for (MethodDeclaration method : methods) {
                if (isTargetMethod(method)) {
                    boolean methodModified = updateMethodParameters(method);
                    if (methodModified) {
                        modified = true;
                        System.out.println("Updated method: " + method.getNameAsString() + " in " + javaFile.getName());
                    }
                }
            }
            
            if (modified) {
                // Write the modified file back
                try (FileOutputStream output = new FileOutputStream(javaFile)) {
                    output.write(LexicalPreservingPrinter.print(cu).getBytes());
                }
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile.getName() + ": " + e.getMessage());
        }
        
        return false;
    }
    
    private static boolean isTargetMethod(MethodDeclaration method) {
        String methodName = method.getNameAsString();
        return METHOD_NAME_NULL_SAFE_GET.equals(methodName) || METHOD_NAME_NULL_SAFE_SET.equals(methodName);
    }
    
    private static boolean updateMethodParameters(MethodDeclaration method) {
        boolean modified = false;
        
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) parameter.getType();
                String typeName = type.getNameAsString();
                
                if (OLD_PARAMETER_TYPE.equals(typeName)) {
                    // Replace the type
                    type.setName(NEW_PARAMETER_TYPE);
                    modified = true;
                }
            }
        }
        
        return modified;
    }
}