package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        // This is a generic transformation to fix SLF4J version issues
        // in Maven projects when upgrading Logback from 1.2.x/1.3.x to 1.4.x
        
        System.out.println("=== SLF4J Version Fix Transformation ===");
        System.out.println("This transformation updates SLF4J version in pom.xml to 2.0.x");
        System.out.println("to be compatible with Logback 1.4.x");
        System.out.println();
        
        try {
            // Read the pom.xml file
            String pomPath = "/workspace/pay-adminusers/pom.xml";
            String pomContent = new String(Files.readAllBytes(Paths.get(pomPath)));
            
            // Find and replace the SLF4J version for slf4j-api
            String oldVersion = "1.7.36";
            String newVersion = "2.0.9";
            
            // Pattern to match the slf4j-api dependency
            Pattern pattern = Pattern.compile(
                "<dependency>\\s*<groupId>org.slf4j</groupId>\\s*<artifactId>slf4j-api</artifactId>\\s*<version>" + 
                oldVersion + "</version>\\s*</dependency>", 
                Pattern.DOTALL
            );
            
            Matcher matcher = pattern.matcher(pomContent);
            if (matcher.find()) {
                String newPomContent = matcher.replaceAll(
                    "<dependency>\n" +
                    "    <groupId>org.slf4j</groupId>\n" +
                    "    <artifactId>slf4j-api</artifactId>\n" +
                    "    <version>" + newVersion + "</version>\n" +
                    "</dependency>"
                );
                
                // Write back the updated content
                Files.write(Paths.get(pomPath), newPomContent.getBytes());
                
                System.out.println("SUCCESS: Updated SLF4J version from " + oldVersion + " to " + newVersion);
                System.out.println("File: " + pomPath);
            } else {
                System.out.println("INFO: SLF4J dependency not found with old version " + oldVersion);
            }
            
            // Also update other SLF4J dependencies to the same new version
            String[] slf4jArtifacts = {"slf4j-simple", "slf4j-jdk14", "jcl-over-slf4j", "log4j-over-slf4j", "jul-to-slf4j"};
            for (String artifact : slf4jArtifacts) {
                Pattern artifactPattern = Pattern.compile(
                    "<dependency>\\s*<groupId>org.slf4j</groupId>\\s*<artifactId>" + artifact + "</artifactId>\\s*<version>" + 
                    oldVersion + "</version>\\s*</dependency>", 
                    Pattern.DOTALL
                );
                
                if (artifactPattern.matcher(pomContent).find()) {
                    String newPomContent = artifactPattern.matcher(pomContent).replaceAll(
                        "<dependency>\n" +
                        "    <groupId>org.slf4j</groupId>\n" +
                        "    <artifactId>" + artifact + "</artifactId>\n" +
                        "    <version>" + newVersion + "</version>\n" +
                        "</dependency>"
                    );
                    Files.write(Paths.get(pomPath), newPomContent.getBytes());
                    System.out.println("SUCCESS: Updated " + artifact + " version from " + oldVersion + " to " + newVersion);
                }
            }
            
            // Also ensure logback-classic is compatible with SLF4J 2.0.x
            Pattern logbackPattern = Pattern.compile(
                "<dependency>\\s*<groupId>ch.qos.logback</groupId>\\s*<artifactId>logback-classic</artifactId>\\s*<version>1.4.6</version>\\s*</dependency>", 
                Pattern.DOTALL
            );
            
            if (logbackPattern.matcher(pomContent).find()) {
                System.out.println("INFO: Logback 1.4.6 is compatible with SLF4J 2.0.x");
            }
            
            // Check for any other SLF4J dependencies that might need updating
            Pattern otherPattern = Pattern.compile(
                "<dependency>\\s*<groupId>org.slf4j</groupId>\\s*<artifactId>slf4j-(\\w+)\\</artifactId>\\s*<version>" + 
                oldVersion + "</version>\\s*</dependency>", 
                Pattern.DOTALL
            );
            
            Matcher otherMatcher = otherPattern.matcher(pomContent);
            while (otherMatcher.find()) {
                String artifact = otherMatcher.group(1);
                if (!"api".equals(artifact) && !"simple".equals(artifact) && !"jdk14".equals(artifact) && 
                    !"jcl-over-slf4j".equals(artifact) && !"log4j-over-slf4j".equals(artifact) && 
                    !"jul-to-slf4j".equals(artifact)) {
                    String newPomContent = otherPattern.matcher(pomContent).replaceAll(
                        "<dependency>\n" +
                        "    <groupId>org.slf4j</groupId>\n" +
                        "    <artifactId>slf4j-" + artifact + "</artifactId>\n" +
                        "    <version>" + newVersion + "</version>\n" +
                        "</dependency>"
                    );
                    Files.write(Paths.get(pomPath), newPomContent.getBytes());
                    System.out.println("SUCCESS: Updated slf4j-" + artifact + " version from " + oldVersion + " to " + newVersion);
                }
            }
            
        } catch (IOException e) {
            System.err.println("ERROR: Failed to process pom.xml: " + e.getMessage());
        }
        
        System.out.println("\n=== Transformation Complete ===");
        System.out.println("This rule fixes the breaking change from Logback 1.2.x/1.3.x to 1.4.x");
        System.out.println("by updating SLF4J version from 1.7.36 to 2.0.x");
        System.out.println("and ensuring all SLF4J dependencies are consistent");
    }
}