package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void transformFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file));
            String transformedContent = content;
            
            // Replace imports
            transformedContent = transformedContent.replaceAll(
                "import javax\\.servlet\\.(.*);",
                "import jakarta.servlet.$1;"
            );
            
            // Replace type references in code (non-imports)
            transformedContent = transformedContent.replaceAll(
                "\\bjavax\\.servlet\\.(\\w+)\\b",
                "jakarta.servlet.$1"
            );
            
            if (!content.equals(transformedContent)) {
                Files.write(file, transformedContent.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
