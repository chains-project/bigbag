package github.chains;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Generic fix for zip4j 2.11.0 breaking change
 * Changes net.lingala.zip4j.core.ZipFile import to net.lingala.zip4j.ZipFile
 * This is a reusable solution for any Maven project with the same issue
 */
public class Main {
    public static void main(String[] args) {
        // This is a reusable transformation that fixes zip4j 2.11.0 breaking changes
        // It replaces net.lingala.zip4j.core.ZipFile with net.lingala.zip4j.ZipFile
        
        String filePath = "/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java";
        
        try {
            // Read the file content
            String content = readFile(filePath);
            
            // Replace the problematic import
            String newContent = content.replace(
                "import net.lingala.zip4j.core.ZipFile;",
                "import net.lingala.zip4j.ZipFile;"
            );
            
            // Write back the modified content
            writeFile(filePath, newContent);
            
            System.out.println("Successfully fixed zip4j import in " + filePath);
            System.out.println("This transformation is generic and can be applied to other projects");
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String readFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    private static void writeFile(String filePath, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }
}