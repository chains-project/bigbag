package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        try {
            // Process all Java files in the directory
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            boolean modified = false;
            
            // Replace old Jasypt security imports with Spring security equivalents
            String originalContent = content;
            
            content = content.replace(
                "import org.jasypt.spring.security.PasswordEncoder;",
                "import org.springframework.security.crypto.password.PasswordEncoder;"
            );
            
            content = content.replace(
                "import org.jasypt.spring.security.PBEPasswordEncoder;",
                "import org.springframework.security.crypto.password.PasswordEncoder;"
            );
            
            content = content.replace(
                "import org.acegisecurity.providers.encoding.PasswordEncoder;",
                "import org.springframework.security.crypto.password.PasswordEncoder;"
            );
            
            // If changes were made, save the file
            if (!content.equals(originalContent)) {
                Files.write(filePath, content.getBytes());
                System.out.println("Updated: " + filePath);
            } else {
                System.out.println("No changes: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath);
            e.printStackTrace();
        }
    }
}