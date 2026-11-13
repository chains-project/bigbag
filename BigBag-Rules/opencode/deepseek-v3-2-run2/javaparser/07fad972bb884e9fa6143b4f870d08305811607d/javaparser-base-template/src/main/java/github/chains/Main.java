package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    private static final String SLF4J_API_GROUP_ID = "org.slf4j";
    private static final String SLF4J_API_ARTIFACT_ID = "slf4j-api";
    private static final String COMPATIBLE_SLF4J_VERSION = "2.0.7";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist or is not a directory: " + sourceDirectory);
            System.exit(1);
        }
        
        try {
            List<Path> pomFiles = findPomFiles(sourcePath);
            System.out.println("Found " + pomFiles.size() + " pom.xml files");
            
            for (Path pomFile : pomFiles) {
                System.out.println("Processing: " + pomFile);
                processPomFile(pomFile);
            }
            
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findPomFiles(Path startDir) throws IOException {
        List<Path> pomFiles = new ArrayList<>();
        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals("pom.xml")) {
                    pomFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return pomFiles;
    }
    
    private static void processPomFile(Path pomFile) throws IOException {
        String content = Files.readString(pomFile);
        
        // Parse the XML content as Java code to use JavaParser
        // We'll treat it as a string and use pattern matching since it's XML
        String transformedContent = transformPomContent(content);
        
        if (!content.equals(transformedContent)) {
            Files.writeString(pomFile, transformedContent);
            System.out.println("Updated: " + pomFile);
        } else {
            System.out.println("No changes needed: " + pomFile);
        }
    }
    
    private static String transformPomContent(String content) {
        // This transformation updates SLF4J API version to be compatible with logback-classic 1.4.1+
        // Pattern: <groupId>org.slf4j</groupId>\s*<artifactId>slf4j-api</artifactId>\s*<version>OLD_VERSION</version>
        
        String slf4jPattern = "<groupId>" + SLF4J_API_GROUP_ID + "</groupId>\\s*<artifactId>" + SLF4J_API_ARTIFACT_ID + "</artifactId>\\s*<version>[^<]*</version>";
        String replacement = "<groupId>" + SLF4J_API_GROUP_ID + "</groupId>\\n            <artifactId>" + SLF4J_API_ARTIFACT_ID + "</artifactId>\\n            <version>" + COMPATIBLE_SLF4J_VERSION + "</version>";
        
        String transformed = content.replaceAll(slf4jPattern, replacement);
        
        // Also check for any other SLF4J dependencies that might need updating
        // Update other SLF4J artifacts to compatible versions
        transformed = updateOtherSlf4jDependencies(transformed);
        
        return transformed;
    }
    
    private static String updateOtherSlf4jDependencies(String content) {
        // Update other common SLF4J dependencies to compatible versions
        String[] slf4jArtifacts = {
            "jul-to-slf4j",
            "log4j-over-slf4j", 
            "jcl-over-slf4j"
        };
        
        String transformed = content;
        for (String artifact : slf4jArtifacts) {
            String pattern = "<groupId>" + SLF4J_API_GROUP_ID + "</groupId>\\s*<artifactId>" + artifact + "</artifactId>\\s*<version>[^<]*</version>";
            String replacement = "<groupId>" + SLF4J_API_GROUP_ID + "</groupId>\\n            <artifactId>" + artifact + "</artifactId>\\n            <version>" + COMPATIBLE_SLF4J_VERSION + "</version>";
            transformed = transformed.replaceAll(pattern, replacement);
        }
        
        return transformed;
    }
}