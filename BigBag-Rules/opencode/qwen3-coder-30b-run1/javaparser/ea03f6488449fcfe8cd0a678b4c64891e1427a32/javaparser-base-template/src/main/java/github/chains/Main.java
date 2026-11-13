package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            boolean modified = false;

            // Fix 1: Remove imports from redis.clients.jedis.commands package
            modified |= fixPipelineImports(cu);
            
            // Fix 2: Replace Client class references with Jedis
            modified |= fixClientClass(cu);
            
            // Fix 3: Fix Pipeline constructor calls
            modified |= fixPipelineConstructors(cu);

            if (modified) {
                // For now, we'll just print that we found modifications
                System.out.println("Would modify: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }

    private static boolean fixPipelineImports(CompilationUnit cu) {
        boolean modified = false;
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        
        // Check for imports that need to be removed
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            // Remove old pipeline imports from redis.clients.jedis.commands
            if (importName.startsWith("redis.clients.jedis.commands.")) {
                importsToRemove.add(importDecl);
                modified = true;
            }
        }
        
        // Remove the problematic imports
        for (ImportDeclaration importToRemove : importsToRemove) {
            cu.remove(importToRemove);
        }
        
        return modified;
    }

    private static boolean fixClientClass(CompilationUnit cu) {
        boolean modified = false;
        
        // Replace Client class references
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if ("Client".equals(type.getNameAsString())) {
                // Check if it's from redis.clients.jedis package
                if (type.getScope().isPresent() && 
                    "redis.clients.jedis".equals(type.getScope().get().asString())) {
                    type.setName("Jedis");
                    modified = true;
                }
            }
        }
        
        // Replace Client constructor calls
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (creation.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) creation.getType();
                if ("Client".equals(type.getNameAsString())) {
                    if (type.getScope().isPresent() && 
                        "redis.clients.jedis".equals(type.getScope().get().asString())) {
                        type.setName("Jedis");
                        modified = true;
                    }
                }
            }
        }
        
        return modified;
    }

    private static boolean fixPipelineConstructors(CompilationUnit cu) {
        boolean modified = false;
        
        // Fix Pipeline constructor calls
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (creation.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) creation.getType();
                if ("Pipeline".equals(type.getNameAsString())) {
                    if (type.getScope().isPresent() && 
                        "redis.clients.jedis".equals(type.getScope().get().asString())) {
                        // Pipeline constructor now requires Jedis instance
                        if (creation.getArguments().isEmpty()) {
                            // Add a Jedis instance as argument
                            ObjectCreationExpr jedisCreation = new ObjectCreationExpr();
                            jedisCreation.setType(new ClassOrInterfaceType("Jedis"));
                            creation.getArguments().add(jedisCreation);
                            modified = true;
                        }
                    }
                }
            }
        }
        
        return modified;
    }
}