package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        try {
            // Process all Java files in the directory
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Create a visitor to make the changes
            HazelcastApiMigrationVisitor visitor = new HazelcastApiMigrationVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes());
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that migrates Hazelcast API calls from older versions to 5.1
     */
    private static class HazelcastApiMigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDeclaration, Void arg) {
            // Migrate imports
            String importName = importDeclaration.getNameAsString();
            if (importName.startsWith("com.hazelcast.core.")) {
                // Check for problematic imports and replace them
                switch (importName) {
                    case "com.hazelcast.core.Member":
                        importDeclaration.setName("com.hazelcast.cluster.Member");
                        return importDeclaration;
                    case "com.hazelcast.core.Cluster":
                        importDeclaration.setName("com.hazelcast.cluster.Cluster");
                        return importDeclaration;
                    case "com.hazelcast.core.MapEvent":
                        importDeclaration.setName("com.hazelcast.map.event.MapEvent");
                        return importDeclaration;
                    case "com.hazelcast.core.EntryEvent":
                        importDeclaration.setName("com.hazelcast.map.event.EntryEvent");
                        return importDeclaration;
                    case "com.hazelcast.core.MemberAttributeEvent":
                        importDeclaration.setName("com.hazelcast.cluster.event.MemberAttributeEvent");
                        return importDeclaration;
                    case "com.hazelcast.core.MembershipEvent":
                        importDeclaration.setName("com.hazelcast.cluster.event.MembershipEvent");
                        return importDeclaration;
                    case "com.hazelcast.core.MembershipListener":
                        importDeclaration.setName("com.hazelcast.cluster.MembershipListener");
                        return importDeclaration;
                    case "com.hazelcast.config.MaxSizeConfig":
                        // MaxSizeConfig still exists in Hazelcast 5.1, but let's keep it as is
                        return importDeclaration;
                    case "com.hazelcast.monitor":
                        // Remove this import as it no longer exists
                        return null;
                }
            }
            return super.visit(importDeclaration, arg);
        }
    }
}

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        try {
            // Process all Java files in the directory
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Create a visitor to make the changes
            HazelcastApiMigrationVisitor visitor = new HazelcastApiMigrationVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes());
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that migrates Hazelcast API calls from older versions to 5.1
     */
    private static class HazelcastApiMigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public ImportDeclaration visit(ImportDeclaration importDeclaration, Void arg) {
            // Migrate imports
            String importName = importDeclaration.getNameAsString();
            if (importName.startsWith("com.hazelcast.core.")) {
                // Check for problematic imports and replace them
                switch (importName) {
                    case "com.hazelcast.core.Member":
                        importDeclaration.setName("com.hazelcast.cluster.Member");
                        return importDeclaration;
                    case "com.hazelcast.core.Cluster":
                        importDeclaration.setName("com.hazelcast.cluster.Cluster");
                        return importDeclaration;
                    case "com.hazelcast.core.MapEvent":
                        importDeclaration.setName("com.hazelcast.map.event.MapEvent");
                        return importDeclaration;
                    case "com.hazelcast.core.EntryEvent":
                        importDeclaration.setName("com.hazelcast.map.event.EntryEvent");
                        return importDeclaration;
                    case "com.hazelcast.core.MemberAttributeEvent":
                        importDeclaration.setName("com.hazelcast.cluster.event.MemberAttributeEvent");
                        return importDeclaration;
                    case "com.hazelcast.core.MembershipEvent":
                        importDeclaration.setName("com.hazelcast.cluster.event.MembershipEvent");
                        return importDeclaration;
                    case "com.hazelcast.core.MembershipListener":
                        importDeclaration.setName("com.hazelcast.cluster.MembershipListener");
                        return importDeclaration;
                    case "com.hazelcast.config.MaxSizeConfig":
                        // MaxSizeConfig still exists in Hazelcast 5.1, but let's keep it as is
                        return importDeclaration;
                    case "com.hazelcast.monitor":
                        // Remove this import as it no longer exists
                        return null;
                }
            }
            return super.visit(importDeclaration, arg);
        }
    }
}