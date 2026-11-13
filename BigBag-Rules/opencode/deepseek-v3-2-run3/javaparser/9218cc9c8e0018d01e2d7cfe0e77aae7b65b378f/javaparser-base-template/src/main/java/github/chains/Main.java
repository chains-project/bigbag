package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for migrating from javax.validation to jakarta.validation
 * This handles the Java EE to Jakarta EE migration for validation APIs.
 */
public class Main {
    private static final String OLD_PACKAGE_PREFIX = "javax.validation";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.validation";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws Exception {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            System.err.println("Directory does not exist: " + directory);
            return;
        }
        
        List<Path> javaFiles = Files.walk(directory)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        for (Path javaFile : javaFiles) {
            processJavaFile(javaFile);
        }
    }
    
    private static void processJavaFile(Path javaFile) throws Exception {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse: " + javaFile)
        );
        
        boolean modified = false;
        
        // Process imports
        for (ImportDeclaration importDecl : cu.findAll(ImportDeclaration.class)) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                importDecl.setName(newImportName);
                modified = true;
                System.out.println("Updated import: " + importName + " -> " + newImportName);
            }
        }
        
        // Process fully qualified names in code using simple text replacement
        // This handles cases like @javax.validation.constraints.NotNull
        String originalContent = content;
        String newContent = content.replaceAll(
            "\\b" + OLD_PACKAGE_PREFIX.replace(".", "\\.") + "\\.", 
            NEW_PACKAGE_PREFIX + "."
        );
        
        if (!originalContent.equals(newContent)) {
            modified = true;
            // Parse again with updated content to get proper AST
            cu = parser.parse(newContent).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse updated content: " + javaFile)
            );
        }
        
        if (modified) {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String finalContent = printer.print(cu);
            
            // Backup original file
            Path backupFile = javaFile.resolveSibling(javaFile.getFileName().toString() + ".bak");
            Files.copy(javaFile, backupFile);
            
            // Write transformed content
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(finalContent);
            }
            
            System.out.println("Transformed: " + javaFile);
        }
    }
}