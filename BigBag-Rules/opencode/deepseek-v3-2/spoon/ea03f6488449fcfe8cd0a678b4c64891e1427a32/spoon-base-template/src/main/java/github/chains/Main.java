package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Main {
    
    private static final String OLD_PACKAGE = "redis.clients.jedis.commands";
    
    // Mapping of old interface names to new interface names
    private static final Map<String, String> INTERFACE_MAPPING = Map.ofEntries(
        Map.entry("BasicRedisPipeline", "PipelineCommands"),
        Map.entry("BinaryRedisPipeline", "PipelineBinaryCommands"),
        Map.entry("BinaryScriptingCommandsPipeline", "ScriptingKeyPipelineBinaryCommands"),
        Map.entry("MultiKeyBinaryRedisPipeline", "PipelineBinaryCommands"),
        Map.entry("MultiKeyCommandsPipeline", "PipelineCommands"),
        Map.entry("RedisPipeline", "PipelineCommands"),
        Map.entry("ScriptingCommandsPipeline", "ScriptingKeyPipelineCommands")
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path file) {
        try {
            String content = Files.readString(file);
            String original = content;
            
            // Process imports
            content = processImports(content);
            
            // Process extends/implements clauses
            content = processSuperInterfaces(content);
            
            if (!content.equals(original)) {
                Files.writeString(file, content);
                System.out.println("Modified: " + file);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
    
    private static String processImports(String content) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.startsWith("import " + OLD_PACKAGE + ".")) {
                // Extract the imported class name
                Matcher matcher = Pattern.compile("import " + Pattern.quote(OLD_PACKAGE) + "\\.(\\w+);").matcher(trimmed);
                if (matcher.matches()) {
                    String simpleName = matcher.group(1);
                    
                    if (INTERFACE_MAPPING.containsKey(simpleName)) {
                        String newInterface = INTERFACE_MAPPING.get(simpleName);
                        String newLine = line.replace(simpleName, newInterface);
                        result.append(newLine).append("\n");
                        System.out.println("  Replaced import: " + simpleName + " -> " + newInterface);
                        continue;
                    } else if (simpleName.equals("ClusterPipeline")) {
                        // Update ClusterPipeline import to new package
                        String newLine = line.replace(OLD_PACKAGE + ".ClusterPipeline", "redis.clients.jedis.ClusterPipeline");
                        result.append(newLine).append("\n");
                        System.out.println("  Updated ClusterPipeline import to new package");
                        continue;
                    }
                }
            }
            
            result.append(line).append("\n");
        }
        
        return result.toString();
    }
    
    private static String processSuperInterfaces(String content) {
        for (Map.Entry<String, String> entry : INTERFACE_MAPPING.entrySet()) {
            String oldInterface = entry.getKey();
            String newInterface = entry.getValue();
            
            // Replace in extends/implements clauses
            String pattern = "\\b" + oldInterface + "\\b(?!\\s*\\()";
            content = content.replaceAll(pattern, newInterface);
        }
        
        // Special handling for ClusterPipeline
        content = content.replaceAll("\\b" + OLD_PACKAGE + "\\.ClusterPipeline\\b", "redis.clients.jedis.ClusterPipeline");
        
        return content;
    }
}