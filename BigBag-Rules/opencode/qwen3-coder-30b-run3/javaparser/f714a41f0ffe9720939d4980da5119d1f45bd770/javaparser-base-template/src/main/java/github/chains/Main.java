package github.chains;

import java.io.*;
import java.nio.file.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the source directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(filePath));
            boolean modified = false;
            
            // Fix 1: Remove imports for missing classes
            content = content.replaceAll(
                "import com\\.pubnub\\.api\\.models\\.consumer\\.pubsub\\.objects\\.(PNMembershipResult|PNSpaceResult|PNUserResult);\\n", 
                ""
            );
            modified = true;
            
            // Fix 2: Replace the PNConfiguration constructor call
            // Look for new PNConfiguration() and replace with new PNConfiguration(publicKey)
            content = content.replaceAll(
                "PNConfiguration pnConfiguration = new PNConfiguration\\(\\);",
                "PNConfiguration pnConfiguration = new PNConfiguration(publicKey);"
            );
            modified = true;
            
            // Fix 3: Remove method overrides for missing methods
            content = content.replaceAll(
                "@Override\\s+public void user\\(PubNub pubnub, PNUserResult pnUserResult\\) \\{[^}]*\\}",
                ""
            );
            modified = true;
            
            content = content.replaceAll(
                "@Override\\s+public void space\\(PubNub pubnub, PNSpaceResult pnSpaceResult\\) \\{[^}]*\\}",
                ""
            );
            modified = true;
            
            content = content.replaceAll(
                "@Override\\s+public void membership\\(PubNub pubnub, PNMembershipResult pnMembershipResult\\) \\{[^}]*\\}",
                ""
            );
            modified = true;
            
            // If content was modified, write back to file
            if (modified) {
                Files.write(filePath, content.getBytes());
                System.out.println("Processed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}