package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    private static final String MAPSTRUCT_GROUP_ID = "org.mapstruct";
    private static final String MAPSTRUCT_ARTIFACT_ID = "mapstruct";
    private static final String MAPSTRUCT_PROCESSOR_ARTIFACT_ID = "mapstruct-processor";
    private static final String TARGET_VERSION = "1.5.0.Final";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing directory: " + sourceDir);
        
        try {
            List<Path> pomFiles = findPomFiles(Paths.get(sourceDir));
            System.out.println("Found " + pomFiles.size() + " pom.xml files");
            
            int modifiedFiles = 0;
            for (Path pomFile : pomFiles) {
                if (processPomFile(pomFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully updated " + modifiedFiles + " pom.xml files");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findPomFiles(Path startDir) throws IOException {
        return Files.walk(startDir)
                .filter(path -> path.toString().endsWith("pom.xml"))
                .collect(Collectors.toList());
    }
    
    private static boolean processPomFile(Path pomFile) {
        try {
            String content = Files.readString(pomFile);
            String originalContent = content;
            
            // Apply transformations
            content = updateMapStructDependency(content);
            content = updateMapStructProcessorDependency(content);
            content = updateDependencyManagement(content);
            
            if (!content.equals(originalContent)) {
                Files.writeString(pomFile, content);
                System.out.println("Updated: " + pomFile);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Error processing " + pomFile + ": " + e.getMessage());
        }
        return false;
    }
    
    private static String updateMapStructDependency(String content) {
        // Pattern: <artifactId>mapstruct</artifactId> followed by version tag
        String pattern = "(<artifactId>" + MAPSTRUCT_ARTIFACT_ID + "</artifactId>\\s*<version>)[^<]*(</version>)";
        return content.replaceAll(pattern, "$1" + TARGET_VERSION + "$2");
    }
    
    private static String updateMapStructProcessorDependency(String content) {
        // Pattern: <artifactId>mapstruct-processor</artifactId> followed by version tag
        String pattern = "(<artifactId>" + MAPSTRUCT_PROCESSOR_ARTIFACT_ID + "</artifactId>\\s*<version>)[^<]*(</version>)";
        return content.replaceAll(pattern, "$1" + TARGET_VERSION + "$2");
    }
    
    private static String updateDependencyManagement(String content) {
        // Pattern: dependencyManagement section with mapstruct dependencies
        String pattern = "(<dependencyManagement>.*?<dependencies>.*?<groupId>" + MAPSTRUCT_GROUP_ID + "</groupId>.*?<artifactId>" + MAPSTRUCT_ARTIFACT_ID + "</artifactId>\\s*<version>)[^<]*(</version>.*?</dependencies>.*?</dependencyManagement>)";
        return content.replaceAll(pattern, "$1" + TARGET_VERSION + "$2");
    }
}