package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        Path pomPath = Paths.get("/workspace/pay-adminusers/pom.xml");
        String content = Files.readString(pomPath);
        
        // Target string with correct indentation
        String target = "<artifactId>slf4j-api</artifactId>\n            <version>1.7.36</version>";
        String replacement = "<artifactId>slf4j-api</artifactId>\n            <version>2.0.6</version>";
        
        if (content.contains(target)) {
            String newContent = content.replace(target, replacement);
            Files.writeString(pomPath, newContent);
            System.out.println("Successfully updated slf4j-api version in pom.xml");
        } else {
             // Try without depending on exact whitespace for newlines
             System.out.println("Exact match failed, trying looser match...");
             String regex = "(<artifactId>slf4j-api</artifactId>\\s*<version>)1.7.36(</version>)";
             if (content.matches("(?s).*" + regex + ".*")) {
                 String newContent = content.replaceAll(regex, "$12.0.6$2");
                 Files.writeString(pomPath, newContent);
                 System.out.println("Successfully updated slf4j-api version in pom.xml using regex");
             } else {
                 System.err.println("Failed to locate dependency block in pom.xml");
                 System.exit(1);
             }
        }
    }
}