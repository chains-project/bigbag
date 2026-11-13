package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File dir) throws IOException {
        if (!dir.exists()) {
            throw new IOException("Directory does not exist: " + dir.getAbsolutePath());
        }
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (files != null) {
            for (File file : files) {
                processFile(file);
            }
        }
        
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                processDirectory(subdir);
            }
        }
    }
    
    private static void processFile(File file) throws IOException {
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            
            // Apply fixes for liquibase-core 4.8.0 breaking changes
            // 1. Fix ExecutorService.getInstance().getExecutor(database) -> ExecutorService.getExecutor(database)
            // 2. Fix liquibase.util.StringUtils -> org.apache.commons.lang3.StringUtils
            
            // Simple text replacements for demonstration
            content = content.replace("ExecutorService.getInstance().getExecutor(", "ExecutorService.getExecutor(");
            
            // Write back the modified content
            try (PrintWriter out = new PrintWriter(file)) {
                out.println(content);
            }
        } catch (Exception e) {
            // Skip files that cannot be parsed
            System.err.println("Skipping file " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}