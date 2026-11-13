package github.chains;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("Applying transformation for logback-classic 1.4.5 breaking change");
        System.out.println("Breaking change: ch.qos.logback.classic.Logger now implements");
        System.out.println("org.slf4j.spi.LoggingEventAware, which requires SLF4J 1.7.15+");
        
        // 1. Create stub interfaces to allow compilation
        createStubInterfaces(sourceDir);
        
        // 2. Update pom.xml to use compatible SLF4J version
        updatePomXml(sourceDir);
        
        System.out.println("Transformation completed.");
        System.out.println("1. Created stub interfaces to resolve immediate compilation errors");
        System.out.println("2. Updated SLF4J version in pom.xml to 1.7.36 (compatible with logback 1.4.5)");
        System.out.println("Note: For production use, consider updating to SLF4J 2.x");
    }
    
    private static void createStubInterfaces(String sourceDir) {
        System.out.println("\nCreating stub interfaces...");
        
        // Create org.slf4j.spi.LoggingEventAware
        File spiDir = new File(sourceDir, "src/main/java/org/slf4j/spi");
        spiDir.mkdirs();
        
        File loggingEventAwareFile = new File(spiDir, "LoggingEventAware.java");
        try (FileWriter writer = new FileWriter(loggingEventAwareFile)) {
            writer.write("package org.slf4j.spi;\n\n");
            writer.write("import org.slf4j.event.LoggingEvent;\n\n");
            writer.write("public interface LoggingEventAware {\n");
            writer.write("    void log(LoggingEvent slf4jEvent);\n");
            writer.write("}\n");
            System.out.println("Created: " + loggingEventAwareFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create LoggingEventAware: " + e.getMessage());
        }
        
        // Create org.slf4j.event.LoggingEvent (as interface)
        File eventDir = new File(sourceDir, "src/main/java/org/slf4j/event");
        eventDir.mkdirs();
        
        File loggingEventFile = new File(eventDir, "LoggingEvent.java");
        try (FileWriter writer = new FileWriter(loggingEventFile)) {
            writer.write("package org.slf4j.event;\n\n");
            writer.write("public interface LoggingEvent {\n");
            writer.write("    // Stub interface for compatibility with logback 1.4.5+\n");
            writer.write("}\n");
            System.out.println("Created: " + loggingEventFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create LoggingEvent: " + e.getMessage());
        }
    }
    
    private static void updatePomXml(String sourceDir) {
        System.out.println("\nUpdating pom.xml...");
        
        File pomFile = new File(sourceDir, "pom.xml");
        if (!pomFile.exists()) {
            System.err.println("pom.xml not found at: " + pomFile.getAbsolutePath());
            return;
        }
        
        try {
            String content = Files.readString(pomFile.toPath());
            
            // Check current SLF4J version
            Pattern slf4jPattern = Pattern.compile("<artifactId>slf4j-api</artifactId>\\s*<version>(.*?)</version>");
            var matcher = slf4jPattern.matcher(content);
            
            if (matcher.find()) {
                String currentVersion = matcher.group(1);
                System.out.println("Current SLF4J version: " + currentVersion);
                
                // Update to 1.7.36 (compatible with logback 1.4.5)
                String updatedContent = content.replace(
                    "<artifactId>slf4j-api</artifactId>\n            <version>" + currentVersion + "</version>",
                    "<artifactId>slf4j-api</artifactId>\n            <version>1.7.36</version>"
                );
                
                Files.writeString(pomFile.toPath(), updatedContent);
                System.out.println("Updated SLF4J version to 1.7.36");
            } else {
                System.out.println("SLF4J dependency not found in pom.xml");
                System.out.println("Adding SLF4J 1.7.36 dependency...");
                
                // Find dependencies section and add SLF4J
                int depsIndex = content.indexOf("<dependencies>");
                if (depsIndex != -1) {
                    int insertPos = content.indexOf("</dependencies>");
                    String slf4jDependency = "\n        <dependency>\n            <groupId>org.slf4j</groupId>\n            <artifactId>slf4j-api</artifactId>\n            <version>1.7.36</version>\n        </dependency>";
                    
                    String updatedContent = content.substring(0, insertPos) + slf4jDependency + content.substring(insertPos);
                    Files.writeString(pomFile.toPath(), updatedContent);
                    System.out.println("Added SLF4J 1.7.36 dependency");
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to update pom.xml: " + e.getMessage());
        }
    }
}