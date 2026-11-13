package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.compiler.ModelBuildingException;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // First, let's manually search and replace in the file
        replaceZipFileReferences(sourceDirectory);
    }
    
    private static void replaceZipFileReferences(String sourceDirectory) {
        // We'll directly process the Java files
        File sourceDir = new File(sourceDirectory);
        if (!sourceDir.isDirectory()) {
            System.err.println("Invalid directory: " + sourceDirectory);
            System.exit(1);
        }
        
        processJavaFiles(sourceDir);
    }
    
    private static void processJavaFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                processJavaFiles(file);
            } else if (file.getName().endsWith(".java")) {
                replaceInFile(file);
            }
        }
    }
    
    private static void replaceInFile(File file) {
        try {
            // Read the file content
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            
            // Replace the specific pattern - the core package was removed
            String newContent = content.replaceAll(
                "net\\.lingala\\.zip4j\\.core\\.ZipFile", 
                "net.lingala.zip4j.ZipFile"
            );
            
            // If changes were made, rewrite the file
            if (!content.equals(newContent)) {
                java.nio.file.Files.write(file.toPath(), newContent.getBytes());
                System.out.println("Updated: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}