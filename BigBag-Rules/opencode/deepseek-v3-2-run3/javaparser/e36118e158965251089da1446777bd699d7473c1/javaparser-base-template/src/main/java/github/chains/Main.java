package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Java files in: " + sourceDir.toAbsolutePath());
        
        // Walk through all Java files
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFile -> {
                try {
                    transformFile(javaFile);
                } catch (Exception e) {
                    System.err.println("Error transforming file: " + javaFile);
                    e.printStackTrace();
                }
            });
        
        System.out.println("Transformation completed.");
    }
    
    private static void transformFile(Path javaFile) throws Exception {
        System.out.println("Processing: " + javaFile);
        
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse: " + javaFile)
        );
        
        boolean hasCactoosMapEntryImport = false;
        boolean hasJavaUtilMapImport = false;
        
        // Check imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.equals("org.cactoos.map.MapEntry")) {
                hasCactoosMapEntryImport = true;
            } else if (importName.equals("java.util.Map")) {
                hasJavaUtilMapImport = true;
            }
        }
        
        // Remove org.cactoos.map.MapEntry import if present
        if (hasCactoosMapEntryImport) {
            cu.getImports().removeIf(importDecl -> 
                importDecl.getNameAsString().equals("org.cactoos.map.MapEntry"));
        }
        
        // Add java.util.Map import if not present and we found cactoos imports
        if (hasCactoosMapEntryImport && !hasJavaUtilMapImport) {
            cu.addImport("java.util.Map");
        }
        
        // Transform all ObjectCreationExpr that create MapEntry
        cu.findAll(ObjectCreationExpr.class).forEach(objectCreation -> {
            if (objectCreation.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = objectCreation.getType().asClassOrInterfaceType();
                String typeName = type.getNameAsString();
                
                // Check if this is creating a MapEntry (could be qualified or simple name)
                if (typeName.equals("MapEntry")) {
                    // Create MethodCallExpr: Map.entry(...)
                    MethodCallExpr mapEntryCall = new MethodCallExpr(
                        new NameExpr("Map"),
                        "entry",
                        objectCreation.getArguments()
                    );
                    
                    // Replace the ObjectCreationExpr with MethodCallExpr
                    objectCreation.replace(mapEntryCall);
                }
            }
        });
        
        // Write the transformed file back
        Files.write(javaFile, cu.toString().getBytes());
    }
}