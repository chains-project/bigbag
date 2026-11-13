package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Generic transformation tool to migrate javax.servlet to jakarta.servlet
 * This is a simple regex-based tool to handle the package rename breaking change
 * in jakarta.servlet-api 6.0.0
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -cp ... github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(new File(sourceDir));
            System.out.println("Transformation completed.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    processJavaFile(file);
                }
            }
        }
    }
    
    private static void processJavaFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        boolean modified = false;
        
        // Replace import statements
        String originalContent = content;
        content = content.replaceAll("import javax\\.servlet\\.(.*?);", "import jakarta.servlet.$1;");
        if (!content.equals(originalContent)) {
            modified = true;
        }
        
        // Replace type references in code
        originalContent = content;
        content = content.replaceAll("\\bjavax\\.servlet\\.(Filter|DispatcherType|FilterRegistration)\\b", "jakarta.servlet.$1");
        if (!content.equals(originalContent)) {
            modified = true;
        }
        
        // Replace references to the packages themselves
        originalContent = content;
        content = content.replaceAll("\\bjavax\\.servlet\\.", "jakarta.servlet.");
        if (!content.equals(originalContent)) {
            modified = true;
        }
        
        // Write back if modified
        if (modified) {
            Files.write(Paths.get(file.getAbsolutePath()), content.getBytes());
            System.out.println("Modified: " + file.getAbsolutePath());
        }
    }
}