package github.chains;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

/**
 * A simple transformation tool to fix MySQL Connector/J breaking changes.
 * Specifically addresses the change from com.mysql.jdbc.exceptions.MySQLTimeoutException
 * to com.mysql.cj.jdbc.exceptions.MySQLTimeoutException.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Define the source directory and the patterns to match
        String sourceDir = args.length > 0 ? args[0] : "/workspace/pdb";
        
        // Process all Java files in the directory
        Path dir = Paths.get(sourceDir);
        if (Files.exists(dir)) {
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        }
    }
    
    private static void processJavaFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            
            // Replace import statements
            String modifiedContent = content.replaceAll(
                "import com\\.mysql\\.jdbc\\.exceptions\\.MySQLTimeoutException;",
                "import com.mysql.cj.jdbc.exceptions.MySQLTimeoutException;"
            );
            
            // If content changed, write back to file
            if (!modifiedContent.equals(content)) {
                Files.write(file, modifiedContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Fixed file: " + file);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
}
