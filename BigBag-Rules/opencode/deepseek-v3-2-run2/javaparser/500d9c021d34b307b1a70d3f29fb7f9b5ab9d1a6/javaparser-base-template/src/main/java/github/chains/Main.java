package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for Jakarta EE MVC API migration from javax.mvc to jakarta.mvc
 * 
 * This transformation handles the breaking change where:
 * - Old API pattern: javax.mvc.* (Java EE namespace)
 * - New API pattern: jakarta.mvc.* (Jakarta EE namespace)
 * 
 * The transformation is parameterized by:
 * - oldPackagePrefix: "javax.mvc"
 * - newPackagePrefix: "jakarta.mvc"
 * 
 * This rule can be applied to any project affected by this namespace migration
 * by simply changing the input source directory path.
 */
public class Main {
    
    // Configuration parameters - can be changed for different transformations
    private static final String OLD_PACKAGE_PREFIX = "javax.mvc";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.mvc";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying transformation from " + OLD_PACKAGE_PREFIX + " to " + NEW_PACKAGE_PREFIX);
        System.out.println("Source directory: " + sourceDirectory);
        
        try {
            transformProject(sourceDirectory);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDirectory) throws IOException {
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            throw new IOException("Source directory does not exist: " + sourceDirectory);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }
    
    private static void transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        
        // Parse the Java file
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow(
            () -> new IOException("Failed to parse file: " + javaFile)
        );
        
        boolean modified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                importDecl.setName(new Name(newImportName));
                modified = true;
                System.out.println("Transformed import: " + importName + " -> " + newImportName);
            }
        }
        
        // Transform fully qualified type references in the AST
        cu.walk(Node.TreeTraversal.PREORDER, node -> {
            if (node instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) node;
                String typeName = type.getNameAsString();
                
                // Check if this is a fully qualified name that needs transformation
                if (typeName.startsWith(OLD_PACKAGE_PREFIX)) {
                    String newTypeName = typeName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                    type.setName(new SimpleName(newTypeName));
                    System.out.println("Transformed type reference: " + typeName + " -> " + newTypeName);
                }
            }
        });
        
        if (modified) {
            // Write the transformed content back to the file
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String transformedContent = printer.print(cu);
            Files.writeString(javaFile, transformedContent);
            System.out.println("Successfully transformed: " + javaFile);
        }
    }
}