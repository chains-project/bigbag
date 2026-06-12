package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleMain {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleMain <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        int totalFixes = 0;
        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            String content = Files.readString(javaFile);
            
            // Count occurrences before
            int beforeCount = countOccurrences(content, "JAXBToStringStrategy.INSTANCE");
            
            // Simple text replacement
            content = content.replace("JAXBToStringStrategy.INSTANCE", "JAXBToStringStrategy.INSTANCE");
            
            // Count occurrences after
            int afterCount = countOccurrences(content, "JAXBToStringStrategy.INSTANCE");
            
            int fixes = beforeCount - afterCount;
            if (fixes > 0) {
                System.out.println("  Fixed: " + fixes + " occurrence(s)");
                totalFixes += fixes;
                Files.writeString(javaFile, content);
            }
        }
        
        System.out.println("Transformation completed successfully! Total fixes: " + totalFixes);
    }
    
    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}