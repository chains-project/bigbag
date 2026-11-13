package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    
    // List of packages that need transformation from javax to jakarta
    private static final List<String> AFFECTED_PACKAGES = Arrays.asList(
        "interceptor",
        "enterprise",
        "annotation", 
        "inject",
        "persistence",
        "ws.rs",
        "ejb",
        "ws"
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming javax.* to jakarta.* imports in: " + sourceDir);
        
        try {
            transformDirectory(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(String directoryPath) throws Exception {
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Directory does not exist: " + directoryPath);
        }
        
        Files.walk(Paths.get(directoryPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    transformFile(path);
                } catch (Exception e) {
                    System.err.println("Error processing file " + path + ": " + e.getMessage());
                }
            });
    }
    
    private static void transformFile(Path filePath) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse file: " + filePath)
        );
        
        final boolean[] modified = new boolean[]{false};
        
        // Visitor to transform imports and type references
        ModifierVisitor<Void> visitor = new ModifierVisitor<Void>() {
            
            @Override
            public Node visit(ImportDeclaration importDecl, Void arg) {
                String importName = importDecl.getNameAsString();
                
                // Check if this import needs transformation (both static and non-static)
                if (importName.startsWith("javax.")) {
                    for (String pkg : AFFECTED_PACKAGES) {
                        if (importName.startsWith("javax." + pkg + ".")) {
                            String newImportName = importName.replaceFirst("^javax\\.", "jakarta.");
                            ImportDeclaration newImport = importDecl.clone();
                            newImport.setName(newImportName);
                            modified[0] = true;
                            return newImport;
                        }
                    }
                    
                    // Also handle javax.ws.rs.* (ws.rs is in the list, but need special handling)
                    if (importName.startsWith("javax.ws.")) {
                        String newImportName = importName.replaceFirst("^javax\\.", "jakarta.");
                        ImportDeclaration newImport = importDecl.clone();
                        newImport.setName(newImportName);
                        modified[0] = true;
                        return newImport;
                    }
                }
                
                return (Node) super.visit(importDecl, arg);
            }
            
            @Override
            public Node visit(Name name, Void arg) {
                String nameStr = name.asString();
                
                // Check if this is a fully-qualified type reference that needs transformation
                if (nameStr.startsWith("javax.")) {
                    for (String pkg : AFFECTED_PACKAGES) {
                        if (nameStr.startsWith("javax." + pkg + ".")) {
                            String newName = nameStr.replaceFirst("^javax\\.", "jakarta.");
                            modified[0] = true;
                            return new Name(newName);
                        }
                    }
                    
                    // Handle javax.ws.*
                    if (nameStr.startsWith("javax.ws.")) {
                        String newName = nameStr.replaceFirst("^javax\\.", "jakarta.");
                        modified[0] = true;
                        return new Name(newName);
                    }
                }
                
                return (Node) super.visit(name, arg);
            }
        };
        
        cu.accept(visitor, null);
        
        if (modified[0]) {
            // Write the transformed file back
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
            String transformedCode = printer.print(cu);
            Files.write(filePath, transformedCode.getBytes());
            System.out.println("Transformed: " + filePath);
        }
    }
}