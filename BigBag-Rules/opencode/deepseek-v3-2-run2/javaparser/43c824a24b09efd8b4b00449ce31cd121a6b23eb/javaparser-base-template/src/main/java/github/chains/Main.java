package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - these can be changed for different dependency migrations
    private static final String OLD_CLASS_NAME = "de.gwdg.metadataqa.api.json.JsonBranch";
    private static final String NEW_CLASS_NAME = "de.gwdg.metadataqa.api.json.DataElement";
    private static final String OLD_METHOD_NAME = "getJsonPath";
    private static final String NEW_METHOD_NAME = "getPath";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            int processedFiles = 0;
            int modifiedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                processedFiles++;
                boolean modified = processFile(javaFile);
                if (modified) {
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile);
                }
            }
            
            System.out.println("\nTransformation completed:");
            System.out.println("  Processed files: " + processedFiles);
            System.out.println("  Modified files: " + modifiedFiles);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        
        // Replace JsonBranch with DataElement
        String newContent = content.replaceAll("JsonBranch", "DataElement");
        
        // Replace getJsonPath() with getPath()
        newContent = newContent.replaceAll("getJsonPath", "getPath");
        
        if (!newContent.equals(content)) {
            Files.write(filePath, newContent.getBytes());
            return true;
        }
        
        return false;
    }
}