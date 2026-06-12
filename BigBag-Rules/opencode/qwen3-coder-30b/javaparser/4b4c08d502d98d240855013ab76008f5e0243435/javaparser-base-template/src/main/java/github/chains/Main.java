package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;

import java.io.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // This transformation fixes Flyway constructor calls that have changed from no parameters to requiring a Configuration parameter
        // It replaces new Flyway() with new Flyway(Flyway.configure())
        // This is a generic rule that can be applied to any project with this breaking change
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Process all Java files in the source directory
        File sourceDir = new File(sourceDirectory);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("Invalid source directory: " + sourceDirectory);
            System.exit(1);
        }
        
        // Walk through all .java files in the directory
        processJavaFiles(sourceDir);
        
        System.out.println("Flyway constructor transformation completed.");
    }
    
    private static void processJavaFiles(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processJavaFiles(file);
                } else if (file.getName().endsWith(".java")) {
                    try {
                        // Read the entire file content
                        String content = readFileToString(file);
                        
                        // Apply the transformation
                        String transformedContent = transformFlywayConstructors(content);
                        
                        // Write back if changed
                        if (!content.equals(transformedContent)) {
                            writeFileFromString(file, transformedContent);
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing file " + file.getAbsolutePath() + ": " + e.getMessage());
                    }
                }
            }
        }
    }
    
    private static String transformFlywayConstructors(String content) {
        // Pattern to match new Flyway() without arguments
        Pattern pattern = Pattern.compile("(new\\s+Flyway\\s*\\(\\s*\\))");
        Matcher matcher = pattern.matcher(content);
        
        StringBuffer result = new StringBuffer();
        boolean found = false;
        
        while (matcher.find()) {
            found = true;
            // Replace with new Flyway(Flyway.configure())
            matcher.appendReplacement(result, "new Flyway(Flyway.configure())");
        }
        
        if (found) {
            matcher.appendTail(result);
            return result.toString();
        } else {
            return content;
        }
    }
    
    private static String readFileToString(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    private static void writeFileFromString(File file, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }
    }
}
