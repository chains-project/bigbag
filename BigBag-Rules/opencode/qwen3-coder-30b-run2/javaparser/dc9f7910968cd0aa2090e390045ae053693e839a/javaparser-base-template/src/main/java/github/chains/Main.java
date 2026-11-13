package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        transformDirectory(sourceDir);
    }
    
    private static void transformDirectory(String sourceDir) throws IOException {
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String transformedContent = transformContent(content);
            
            if (!content.equals(transformedContent)) {
                Files.write(filePath, transformedContent.getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static String transformContent(String content) {
        // Transform constructor: new Flyway() -> new Flyway(FluentConfiguration.configure().load())
        content = content.replaceAll(
            "new Flyway\\(\\)",
            "new Flyway(org.flywaydb.core.api.configuration.FluentConfiguration.configure().load())"
        );
        
        // Transform setter calls to fluent calls
        content = content.replaceAll(
            "(\\w+)\\.setDataSource\\(([^)]+)\\)",
            "$1.dataSource($2)"
        );
        
        content = content.replaceAll(
            "(\\w+)\\.setClassLoader\\(([^)]+)\\)", 
            "$1.classLoader($2)"
        );
        
        content = content.replaceAll(
            "(\\w+)\\.setLocations\\(([^)]+)\\)",
            "$1.locations($2)"
        );
        
        content = content.replaceAll(
            "(\\w+)\\.setValidateOnMigrate\\(([^)]+)\\)",
            "$1.validateOnMigrate($2)"
        );
        
        return content;
    }
}