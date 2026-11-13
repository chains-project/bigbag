package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);

        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        // Find all Java files in the source directory
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

        int modifiedCount = 0;
        for (Path javaFile : javaFiles) {
            try {
                if (processJavaFile(javaFile)) {
                    modifiedCount++;
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Modified " + modifiedCount + " files.");
    }

    private static boolean processJavaFile(Path javaFilePath) throws IOException {
        String content = Files.readString(javaFilePath);
        CompilationUnit cu = StaticJavaParser.parse(content);

        boolean modified = false;

        // Process imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration imp : imports) {
            String importName = imp.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                // Replace javax.servlet imports with jakarta.servlet
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                imp.setName(newImport);
                modified = true;
            }
        }

        // Process class declarations for inheritance and implementation
        for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            // Update any extended types
            if (clazz.getExtendedTypes() != null) {
                for (ClassOrInterfaceType type : clazz.getExtendedTypes()) {
                    updateTypeReference(type, "javax.servlet", "jakarta.servlet");
                }
            }
            
            // Update any implemented interfaces
            if (clazz.getImplementedTypes() != null) {
                for (ClassOrInterfaceType type : clazz.getImplementedTypes()) {
                    updateTypeReference(type, "javax.servlet", "jakarta.servlet");
                }
            }
        }

        // Process method declarations for parameter and return type references
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            // Check return type
            if (method.getType() != null) {
                Type returnType = method.getType();
                updateTypeReference(returnType, "javax.servlet", "jakarta.servlet");
            }

            // Check parameters
            for (Parameter param : method.getParameters()) {
                if (param.getType() != null) {
                    Type paramType = param.getType();
                    updateTypeReference(paramType, "javax.servlet", "jakarta.servlet");
                }
            }
        }

        // Save the file if modified
        if (modified) {
            String newContent = cu.toString();
            Files.writeString(javaFilePath, newContent);
        }
        
        return modified;
    }
    
    private static void updateTypeReference(Type type, String fromPackage, String toPackage) {
        if (type instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
            String oldTypeName = classType.asString();
            
            if (oldTypeName.contains(fromPackage)) {
                String newTypeName = oldTypeName.replace(fromPackage, toPackage);
                classType.setName(newTypeName);
            }
        }
    }
}