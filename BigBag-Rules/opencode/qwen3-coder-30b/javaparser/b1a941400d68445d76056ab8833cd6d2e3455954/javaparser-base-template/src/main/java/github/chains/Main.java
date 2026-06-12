package github.chains;

import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/fluxtion";
        processDirectory(new File(sourceDir));
    }
    
    private static void processDirectory(File dir) throws Exception {
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Invalid directory: " + dir);
            return;
        }
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (files != null) {
            for (File file : files) {
                processFile(file);
            }
        }
        
        // Process subdirectories
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                processDirectory(subdir);
            }
        }
    }
    
    private static void processFile(File file) throws Exception {
        if (!file.getName().endsWith(".java")) {
            return;
        }
        
        // Read the file content
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        
        String originalContent = content.toString();
        
        // Find and replace SnakeYAML Constructor calls with no arguments
        // Pattern: new Constructor() -> new Constructor(new LoaderOptions())
        String modifiedContent = content.toString().replaceAll(
            "new Constructor\\(\\)",
            "new Constructor(new org.yaml.snakeyaml.LoaderOptions())"
        );
        
        // If the file was modified, write it back
        if (!modifiedContent.equals(originalContent)) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(modifiedContent);
            writer.close();
            System.out.println("Modified: " + file.getAbsolutePath());
        }
    }
}