package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + args[0]);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String modifiedContent = transformFlywayConfiguration(content);
            
            if (!content.equals(modifiedContent)) {
                Files.write(filePath, modifiedContent.getBytes());
                System.out.println("Updated: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static String transformFlywayConfiguration(String content) {
        // Pattern 1: The main flyway() method with problematic setter calls
        // Replace the entire Flyway configuration with the new fluent API approach
        String pattern1 = 
            "(\\s*final org\\.flywaydb\\.core\\.Flyway flyway = new Flyway\\(\\);\\n)" +
            "(\\s*flyway\\.setDataSource\\([^;]+;\\n)" +
            "(\\s*flyway\\.setClassLoader\\([^;]+;\\n)" +
            "(\\s*flyway\\.setLocations\\([^;]+;\\n)" +
            "(\\s*flyway\\.setValidateOnMigrate\\([^;]+;\\n)" +
            "(\\s*return flyway;)")
            
        String replacement1 = 
            "$1" +
            "        flyway = Flyway.configure().dataSource(this.dataSource())\n" +
            "                .classLoader(NisAppConfig.class.getClassLoader())\n" +
            "                .locations(prop.getProperty(\"flyway.locations\"))\n" +
            "                .validateOnMigrate(Boolean.valueOf(prop.getProperty(\"flyway.validate\")))\n" +
            "                .load();\n" +
            "$2" +
            "$3" +
            "$4" +
            "$5";

        content = content.replaceAll(pattern1, replacement1);
        
        // Pattern 2: The simpler TestConf case
        String pattern2 = 
            "(\\s*final Flyway flyway = new Flyway\\(\\);\\n)" +
            "(\\s*flyway\\.setDataSource\\([^;]+;\\n)" +
            "(\\s*flyway\\.setLocations\\([^;]+;\\n)" +
            "(\\s*return flyway;)")
            
        String replacement2 = 
            "$1" +
            "        flyway = Flyway.configure().dataSource(this.dataSource())\n" +
            "                .locations(\"db/h2\")\n" +
            "                .load();\n" +
            "$2" +
            "$3";

        content = content.replaceAll(pattern2, replacement2);
        
        // Remove any remaining legacy setter calls
        content = content.replaceAll("\\s*flyway\\.setClassLoader\\([^;]+;\\n", "");
        content = content.replaceAll("\\s*flyway\\.setLocations\\([^;]+;\\n", "");
        content = content.replaceAll("\\s*flyway\\.setValidateOnMigrate\\([^;]+;\\n", "");
        
        return content;
    }
}