package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Walk through all Java files in the source directory and fix enableLogging calls
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String modifiedContent = removeEnableLoggingCalls(content);
            
            if (!content.equals(modifiedContent)) {
                Files.write(filePath, modifiedContent.getBytes());
                System.out.println("Fixed file: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Remove enableLogging calls from the source code
     */
    private static String removeEnableLoggingCalls(String content) {
        // Pattern to match enableLogging calls like:
        // unArchiver.enableLogging(new ConsoleLogger(Logger.LEVEL_DEBUG, "Sauce"));
        // or similar variations
        Pattern pattern = Pattern.compile(
            "\\s*\\w+\\.enableLogging\\([^;]*;\\s*\\n", 
            Pattern.MULTILINE
        );
        
        return pattern.matcher(content).replaceAll("");
    }
}
