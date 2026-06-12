package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        // Generic Spoon transformation for fixing SLF4J version incompatibility with Logback 1.4.4
        // This fixes the issue where projects use SLF4J 1.7.36 with Logback 1.4.4
        // which is incompatible - Logback 1.4.4 requires SLF4J 2.0+
        
        // Usage: java -cp spoon.classpath.tmp github.chains.Main /path/to/project
        if (args.length == 0) {
            System.err.println("Usage: java -cp spoon.classpath.tmp github.chains.Main /path/to/project");
            System.exit(1);
        }
        
        String projectPath = args[0];
        String pomPath = projectPath + "/pom.xml";
        
        try {
            // Read the pom.xml file
            String content = new String(Files.readAllBytes(Paths.get(pomPath)));
            
            // Pattern to match slf4j-api version 1.7.36
            Pattern pattern = Pattern.compile(
                "<groupId>org\\.slf4j</groupId>\\s*<artifactId>slf4j-api</artifactId>\\s*<version>1\\.7\\.36</version>",
                Pattern.DOTALL
            );
            
            // Replace with newer compatible version
            String updatedContent = pattern.matcher(content)
                .replaceAll("<groupId>org.slf4j</groupId>\n" +
                           "            <artifactId>slf4j-api</artifactId>\n" +
                           "            <version>2.0.9</version>");
            
            // Also update any direct references to slf4j-api version in properties
            updatedContent = updatedContent.replaceAll(
                "<slf4j.version>1\\.7\\.36</slf4j.version>",
                "<slf4j.version>2.0.9</slf4j.version>"
            );
            
            // Write back to file
            Files.write(Paths.get(pomPath), updatedContent.getBytes());
            
            System.out.println("Successfully updated SLF4J version in " + pomPath);
            System.out.println("Changed from 1.7.36 to 2.0.9");
            
        } catch (IOException e) {
            System.err.println("Error processing pom.xml: " + e.getMessage());
            System.exit(1);
        }
    }
}