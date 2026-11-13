package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    private static final String OLD_PACKAGE_PREFIX = "javax.validation";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.validation";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            List<Path> modifiedFiles = processDirectory(sourcePath);
            System.out.println("Successfully processed " + modifiedFiles.size() + " files");
            
            if (!modifiedFiles.isEmpty()) {
                System.out.println("Modified files:");
                for (Path file : modifiedFiles) {
                    System.out.println("  " + file);
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static List<Path> processDirectory(Path sourceDir) throws IOException {
        List<Path> modifiedFiles = new ArrayList<>();
        
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    if (processJavaFile(file)) {
                        modifiedFiles.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return modifiedFiles;
    }
    
    private static boolean processJavaFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        boolean modified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                importDecl.setName(new Name(newImportName));
                modified = true;
            }
        }
        
        // Transform fully qualified names in code
        String transformedContent = cu.toString();
        if (transformedContent.contains(OLD_PACKAGE_PREFIX)) {
            transformedContent = transformedContent.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
            modified = true;
        }
        
        if (modified) {
            Files.writeString(javaFile, transformedContent);
            return true;
        }
        
        return false;
    }
}