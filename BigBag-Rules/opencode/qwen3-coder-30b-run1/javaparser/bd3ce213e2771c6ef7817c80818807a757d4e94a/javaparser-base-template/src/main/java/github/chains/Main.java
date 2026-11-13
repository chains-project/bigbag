package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java -jar transformer.jar <source-directory>");
            return;
        }
        
        String sourceDir = args[0];
        File directory = new File(sourceDir);
        
        // Process all pom.xml files in the directory
        processDirectory(directory);
        
        System.out.println("Transformation completed successfully");
    }
    
    private static void processDirectory(File directory) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file);
            } else if (file.getName().equals("pom.xml")) {
                processPomFile(file);
            }
        }
    }
    
    private static void processPomFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        
        // Fix Jackson version mismatch by updating all Jackson dependencies to the same version
        // This addresses the breaking change where jackson-core 2.10.0 doesn't have StreamWriteException
        // from jackson-databind 2.13.4.1
        
        // Update jackson-databind to a compatible version
        content = content.replaceAll(
            "(<dependency>\\s*<groupId>com\\.fasterxml\\.jackson\\.core</groupId>\\s*<artifactId>jackson-databind</artifactId>\\s*<version>)([^<]+)(</version>)",
            "$12.15.3$3"
        );
        
        // Update jackson-core to match
        content = content.replaceAll(
            "(<dependency>\\s*<groupId>com\\.fasterxml\\.jackson\\.core</groupId>\\s*<artifactId>jackson-core</artifactId>\\s*<version>)([^<]+)(</version>)",
            "$12.15.3$3"
        );
        
        // Update jackson-annotations to match
        content = content.replaceAll(
            "(<dependency>\\s*<groupId>com\\.fasterxml\\.jackson\\.core</groupId>\\s*<artifactId>jackson-annotations</artifactId>\\s*<version>)([^<]+)(</version>)",
            "$12.15.3$3"
        );
        
        // Write the modified file back
        Files.write(Paths.get(file.getAbsolutePath()), content.getBytes());
        
        System.out.println("Fixed Jackson dependencies in: " + file.getAbsolutePath());
    }
}