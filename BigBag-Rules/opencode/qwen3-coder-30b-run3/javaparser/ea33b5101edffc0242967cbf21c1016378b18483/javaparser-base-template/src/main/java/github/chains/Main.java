package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Generic transformation rule to fix Flyway instantiation issues caused by breaking API changes.
 * This transformer addresses the breaking change in Flyway where:
 * 1. The no-argument constructor 'new Flyway()' is deprecated/removed
 * 2. Setter methods like setDataSource(), setLocations(), etc. are no longer available
 * 
 * Transforms from old pattern:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(...);
 *   flyway.setLocations(...);
 *   ...
 *   
 * To new pattern:
 *   Flyway flyway = Flyway.configure()
 *       .dataSource(...)
 *       .locations(...)
 *       .load();
 */
public class Main {
    public static void main(String[] args) throws IOException {
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/nem";
        
        // Process all Java files in the directory
        Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path path) {
        try {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            
            // Apply the transformation
            String transformedContent = transformFlywayInstantiation(content);
            
            // Write back to file if changed
            if (!content.equals(transformedContent)) {
                Files.write(path, transformedContent.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
        }
    }
    
    /**
     * Transform Flyway instantiation from old pattern to new pattern
     */
    private static String transformFlywayInstantiation(String content) {
        // Pattern that matches the old Flyway instantiation with setters
        // This pattern looks for this exact sequence:
        // Flyway flyway = new Flyway();
        // flyway.setDataSource(...); 
        // flyway.setLocations(...);
        // [other setter calls...]
        // [return statement]
        
        // For now, we'll do a simple regex-based approach
        // This won't be perfect, but it's easier to get working
        
        // Pattern to match the old Flyway instantiation and setter calls
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n");
        boolean inFlywayBlock = false;
        List<String> blockLines = new ArrayList<>();
        boolean replaced = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Match the beginning of a Flyway instantiation block
            if (line.trim().matches(".*new\\s+Flyway\\(\\).*") && 
                line.contains("=") && 
                line.contains("Flyway")) {
                inFlywayBlock = true;
                blockLines.clear();
                blockLines.add(line);
            } else if (inFlywayBlock) {
                blockLines.add(line);
                
                // If we see the first setter call that matches our pattern
                if (line.trim().startsWith("flyway.set")) {
                    // Continue collecting lines until we find the end of the block
                    if (line.trim().endsWith(";")) {
                        // Check if this is the last line of the block
                        if (i + 1 < lines.length) {
                            String nextLine = lines[i + 1].trim();
                            if (nextLine.equals("return flyway;") || 
                                nextLine.startsWith("return ") ||
                                nextLine.equals("}")) {
                                // We have a complete block to transform
                                String newContent = buildNewFlywayPattern(blockLines);
                                if (newContent != null) {
                                    result.append(newContent);
                                    replaced = true;
                                    inFlywayBlock = false;
                                    continue; // Skip processing these lines
                                }
                            }
                        }
                    }
                } else if (line.trim().equals("return flyway;") || 
                          line.trim().equals("}")) {
                    // End of block reached without detecting a matching pattern
                    inFlywayBlock = false;
                    for (String l : blockLines) {
                        result.append(l).append("\n");
                    }
                }
            } else {
                result.append(line).append("\n");
            }
        }
        
        if (replaced) {
            return result.toString();
        } else {
            return content; // Return unchanged if no transformation was made
        }
    }
    
    private static String buildNewFlywayPattern(List<String> lines) {
        // This is a simplified approach - in a real implementation, we'd parse the AST better
        // For now, we'll just do a string replacement
        
        // Find all the setter calls
        StringBuilder fluentBuilder = new StringBuilder();
        boolean hasDataSource = false;
        boolean hasLocations = false;
        
        for (String line : lines) {
            if (line.contains("setDataSource")) {
                hasDataSource = true;
                // Extract the argument for dataSource
                String arg = extractArgument(line, "setDataSource");
                fluentBuilder.append("        .dataSource(").append(arg).append(")\n");
            } else if (line.contains("setLocations")) {
                hasLocations = true;
                // Extract the argument for locations
                String arg = extractArgument(line, "setLocations");
                fluentBuilder.append("        .locations(").append(arg).append(")\n");
            } else if (line.contains("setValidateOnMigrate")) {
                String arg = extractArgument(line, "setValidateOnMigrate");
                fluentBuilder.append("        .validateOnMigrate(").append(arg).append(")\n");
            } else if (line.contains("setClassLoader")) {
                String arg = extractArgument(line, "setClassLoader");
                fluentBuilder.append("        .classLoader(").append(arg).append(")\n");
            }
        }
        
        // Replace the initialization block with the new pattern
        if (hasDataSource || hasLocations) {
            StringBuilder newPattern = new StringBuilder();
            newPattern.append("        Flyway flyway = Flyway.configure()\n");
            newPattern.append(fluentBuilder);
            newPattern.append("        .load();\n");
            
            // Replace the old pattern with new one, keeping the variable declaration
            return newPattern.toString();
        }
        return null;
    }
    
    private static String extractArgument(String line, String setterName) {
        // Extract argument from line like: flyway.setDataSource(this.dataSource());
        String pattern = setterName + "\\((.*)\\)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
}
