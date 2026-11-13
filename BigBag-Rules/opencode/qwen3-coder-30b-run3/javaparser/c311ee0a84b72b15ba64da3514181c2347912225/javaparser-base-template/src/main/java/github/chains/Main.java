package github.chains;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic transformation tool to fix Hamcrest constructor breaking changes.
 * Fixes patterns like:
 * - new StringContains(true, "expected") -> new StringContains("expected")
 * - new StringContains(false, "expected") -> new StringContains("expected")
 * - new StringStartsWith(false, "expected") -> new StringStartsWith("expected")
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        try {
            processDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            throw new IOException("Directory does not exist: " + directory.getAbsolutePath());
        }

        Files.walk(Paths.get(directory.getAbsolutePath()))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String fixedContent = fixHamcrestConstructors(content);
            
            if (!content.equals(fixedContent)) {
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(fixedContent);
                }
                System.out.println("Fixed: " + filePath);
            } else {
                System.out.println("No changes needed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Fix Hamcrest constructor calls that have broken signatures
     */
    private static String fixHamcrestConstructors(String content) {
        // Pattern to match StringContains constructor calls with boolean parameter
        Pattern stringContainsPattern = Pattern.compile(
            "new\\s+StringContains\\s*\\(\\s*(true|false)\\s*,\\s*\"([^\"]*)\"\\s*\\)");
        
        // Pattern to match StringStartsWith constructor calls with boolean parameter
        Pattern stringStartsWithPattern = Pattern.compile(
            "new\\s+StringStartsWith\\s*\\(\\s*(true|false)\\s*,\\s*\"([^\"]*)\"\\s*\\)");
        
        StringBuffer result = new StringBuffer();
        
        // Fix StringContains calls
        Matcher matcher = stringContainsPattern.matcher(content);
        boolean changed = false;
        while (matcher.find()) {
            changed = true;
            // Replace with just the string parameter
            matcher.appendReplacement(result, "new StringContains(\"$2\")");
        }
        matcher.appendTail(result);
        
        // Now work with the result for StringStartsWith
        String tempResult = result.toString();
        result = new StringBuffer();
        matcher = stringStartsWithPattern.matcher(tempResult);
        while (matcher.find()) {
            changed = true;
            // Replace with just the string parameter
            matcher.appendReplacement(result, "new StringStartsWith(\"$2\")");
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
}