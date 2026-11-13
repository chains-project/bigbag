package github.chains;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String OLD_PACKAGE = "develop.p2p.lib";
    private static final String NEW_PACKAGE = "tokyo.peya.lib";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming package references from " + OLD_PACKAGE + " to " + NEW_PACKAGE);
        System.out.println("Source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedFiles + " files");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean transformFile(Path filePath) throws Exception {
        String content = new String(Files.readAllBytes(filePath));
        String originalContent = content;
        
        content = content.replace(OLD_PACKAGE, NEW_PACKAGE);
        
        if (!content.equals(originalContent)) {
            Files.write(filePath, content.getBytes());
            System.out.println("Updated: " + filePath);
            return true;
        }
        
        return false;
    }
}