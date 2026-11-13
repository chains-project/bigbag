package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for migrating from javax.mvc to jakarta.mvc namespace.
 * This transformation can be applied to any Java project affected by the Jakarta EE namespace change.
 */
public class Main {
    
    // Configuration parameters - can be changed for different namespace migrations
    private static final String OLD_NAMESPACE = "javax";
    private static final String NEW_NAMESPACE = "jakarta";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming MVC namespace from " + OLD_NAMESPACE + " to " + NEW_NAMESPACE);
        System.out.println("Source directory: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws IOException {
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }
    
    private static void transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser(new ParserConfiguration());
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow();
        
        boolean[] modified = {false};
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith(OLD_NAMESPACE)) {
                String newImportName = importName.replace(OLD_NAMESPACE, NEW_NAMESPACE);
                importDecl.setName(new Name(newImportName));
                modified[0] = true;
                System.out.println("  Updated import: " + importName + " -> " + newImportName);
            }
        }
        
        // Transform fully qualified type references in the AST
        cu.walk(Node.class, node -> {
            if (node instanceof Name) {
                Name name = (Name) node;
                String nameString = name.asString();
                
                // Check if this is a fully qualified name that needs transformation
                if (nameString.startsWith(OLD_NAMESPACE)) {
                    String newNameString = nameString.replace(OLD_NAMESPACE, NEW_NAMESPACE);
                    name.replace(new Name(newNameString));
                    modified[0] = true;
                    System.out.println("  Updated type reference: " + nameString + " -> " + newNameString);
                }
            }
        });
        
        if (modified[0]) {
            // Write back the transformed content
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String transformedContent = printer.print(cu);
            Files.writeString(javaFile, transformedContent);
            System.out.println("✓ Updated file: " + javaFile);
        }
    }
}