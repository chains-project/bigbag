package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation for migrating javax.annotation.Priority to jakarta.annotation.Priority
 */
public class Main {
    private static final String OLD_ANNOTATION = "javax.annotation.Priority";
    private static final String NEW_ANNOTATION = "jakarta.annotation.Priority";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path dirPath = Paths.get(sourceDir);
        
        if (!Files.exists(dirPath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        // Process all Java files recursively
        try (Stream<Path> paths = Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path filePath : javaFiles) {
                processJavaFile(filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processJavaFile(Path filePath) throws IOException {
        String content = new String(Files.readAllBytes(filePath));
        CompilationUnit cu = StaticJavaParser.parse(content);

        boolean modified = false;

        // Replace import statements
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration imp : imports) {
            if (imp.getNameAsString().equals(OLD_ANNOTATION)) {
                imp.setName(NEW_ANNOTATION);
                modified = true;
            }
        }

        if (modified) {
            // Write back to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            
            System.out.println("Updated file: " + filePath);
        }
    }
}
