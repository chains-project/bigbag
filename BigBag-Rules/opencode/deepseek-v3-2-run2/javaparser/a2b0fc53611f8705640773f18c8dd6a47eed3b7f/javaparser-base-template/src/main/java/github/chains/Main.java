package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            return;
        }
        
        Path sourceDir = Paths.get(args[0]);
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        System.out.println("Transforming " + javaFiles.size() + " Java files...");
        
        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            transformFile(javaFile);
        }
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        
        // Pattern 1: Direct Flyway construction with setters pattern
        // Matches: final Flyway flyway = new Flyway();
        //          flyway.setDataSource(...);
        //          flyway.setClassLoader(...);
        //          etc.
        
        String transformed = transformFlywayPattern(content);
        
        Files.write(filePath, transformed.getBytes());
    }
    
    private static String transformFlywayPattern(String content) {
        // This is a simplified regex-based transformation
        // In a real implementation, we would use JavaParser for robust AST manipulation
        
        // Pattern: Find "new Flyway()" followed by setters
        String pattern = "(final\\s+)?(org\\.flywaydb\\.core\\.)?Flyway\\s+(\\w+)\\s*=\\s*new\\s+Flyway\\(\\)\\s*;";
        
        // For now, we'll do a simple text replacement
        // This is NOT production-ready but demonstrates the concept
        
        StringBuilder result = new StringBuilder(content);
        
        // Simple approach: Replace "new Flyway()" with "new Flyway(configuration)"
        // and add configuration creation before it
        
        int index = 0;
        while ((index = result.indexOf("new Flyway()", index)) != -1) {
            // Look backwards to find variable declaration
            int varStart = result.lastIndexOf("Flyway ", index);
            if (varStart == -1) {
                index += 12; // length of "new Flyway()"
                continue;
            }
            
            // Find variable name
            int varNameStart = varStart + 6; // after "Flyway "
            int varNameEnd = result.indexOf(" ", varNameStart);
            if (varNameEnd == -1 || varNameEnd > index) {
                varNameEnd = result.indexOf("=", varNameStart);
            }
            if (varNameEnd == -1 || varNameEnd > index) {
                index += 12;
                continue;
            }
            
            String varName = result.substring(varNameStart, varNameEnd).trim();
            
            // Replace constructor
            result.replace(index, index + 12, "new Flyway(configuration)");
            
            // Insert configuration declaration before this line
            int lineStart = result.lastIndexOf("\n", index) + 1;
            String configDecl = "final org.flywaydb.core.api.configuration.ClassicConfiguration configuration = new org.flywaydb.core.api.configuration.ClassicConfiguration();\n";
            result.insert(lineStart, configDecl);
            
            // Now we need to find and replace method calls on the flyway variable
            // Search for pattern: varName.setMethodName(...)
            int searchStart = index + 22; // after "new Flyway(configuration)"
            while (true) {
                int methodStart = result.indexOf(varName + ".set", searchStart);
                if (methodStart == -1) {
                    break;
                }
                
                // Check if this is really a method call on our variable
                int dotPos = methodStart + varName.length();
                if (result.charAt(dotPos) != '.') {
                    searchStart = methodStart + 1;
                    continue;
                }
                
                // Find the method name
                int methodNameStart = dotPos + 1;
                int parenPos = result.indexOf("(", methodNameStart);
                if (parenPos == -1) {
                    break;
                }
                
                String methodName = result.substring(methodNameStart, parenPos);
                
                // Check if it's one of the config methods
                if (methodName.equals("setDataSource") || methodName.equals("setClassLoader") || 
                    methodName.equals("setLocations") || methodName.equals("setValidateOnMigrate")) {
                    // Replace varName. with configuration.
                    result.replace(methodStart, methodStart + varName.length(), "configuration");
                    
                    // Special case: setLocations(String) should become setLocationsAsStrings(String)
                    if (methodName.equals("setLocations")) {
                        // Check if the argument is a String (not Location[])
                        int argStart = parenPos + 1;
                        int argEnd = result.indexOf(")", argStart);
                        if (argEnd != -1) {
                            String arg = result.substring(argStart, argEnd).trim();
                            // Simple heuristic: if it's a string literal or string variable
                            if ((arg.startsWith("\"") && arg.endsWith("\"")) || 
                                (!arg.contains("new Location") && !arg.contains("Location["))) {
                                // Change method name to setLocationsAsStrings
                                result.replace(methodNameStart, parenPos, "setLocationsAsStrings");
                            }
                        }
                    }
                }
                
                searchStart = parenPos + 1;
            }
            
            // Update index past our changes
            index += configDecl.length() + 22; // length of configDecl + "new Flyway(configuration)"
        }
        
        return result.toString();
    }
}
