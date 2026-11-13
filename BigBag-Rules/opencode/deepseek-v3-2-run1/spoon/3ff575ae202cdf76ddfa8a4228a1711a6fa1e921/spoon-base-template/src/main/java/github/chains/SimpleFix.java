package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple file-based fix for the PlexusContainer.getLoggerManager() issue.
 * This is a simpler alternative to the Spoon transformation.
 */
public class SimpleFix {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.SimpleFix <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Scanning directory: " + sourceDir);
        
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int fixedCount = 0;
            for (Path file : javaFiles) {
                if (fixFile(file)) {
                    fixedCount++;
                }
            }
            
            System.out.println("Fixed " + fixedCount + " file(s)");
        }
    }
    
    private static boolean fixFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        boolean modified = false;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // Look for pattern: getContainer().getLoggerManager()
            if (line.contains("getContainer().getLoggerManager()")) {
                // Replace with ((MutablePlexusContainer) getContainer()).getLoggerManager()
                String newLine = line.replace(
                    "getContainer().getLoggerManager()", 
                    "((MutablePlexusContainer) getContainer()).getLoggerManager()"
                );
                
                if (!newLine.equals(line)) {
                    lines.set(i, newLine);
                    modified = true;
                    System.out.println("Fixed: " + file + " line " + (i + 1));
                }
            }
        }
        
        if (modified) {
            Files.write(file, lines);
            return true;
        }
        
        return false;
    }
}