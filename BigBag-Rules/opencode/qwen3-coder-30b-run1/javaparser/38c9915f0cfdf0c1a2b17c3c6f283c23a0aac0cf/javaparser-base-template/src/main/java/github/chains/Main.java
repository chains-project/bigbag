package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic JavaParser transformation to fix breaking dependency changes in cactoos.
 * This transformation handles cases where classes have been removed or renamed in newer versions.
 * 
 * The transformation replaces old cactoos class references with their new equivalents.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String modifiedContent = content;
            
            // Handle import statements first
            // Replace Filtered import from collection to iterable
            modifiedContent = modifiedContent.replaceAll(
                "import\\s+org\\.cactoos\\.collection\\.Filtered;",
                "import org.cactoos.iterable.Filtered;"
            );
            
            // Replace CollectionOf import from collection to iterable
            modifiedContent = modifiedContent.replaceAll(
                "import\\s+org\\.cactoos\\.collection\\.CollectionOf;",
                "import org.cactoos.collection.CollectionOf;"
            );
            
            // Handle constructor calls
            // 1. Replace LengthOf with size() call
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+LengthOf\\s*\\(([^)]+)\\)", 
                "$1.size()"
            );
            
            // 2. Replace RandomText with new org.cactoos.text.RandomText()
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+RandomText\\s*\\([^)]*\\)", 
                "new org.cactoos.text.RandomText()"
            );
            
            // 3. Replace Filtered from collection to iterable package
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+Filtered\\s*\\(([^)]+)\\)", 
                "new org.cactoos.iterable.Filtered($1)"
            );
            
            // 4. Replace CheckedScalar
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+CheckedScalar\\s*\\(([^)]+)\\)", 
                "new org.cactoos.scalar.CheckedScalar($1)"
            );
            
            // 5. Replace UncheckedScalar
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+UncheckedScalar\\s*\\(([^)]+)\\)", 
                "new org.cactoos.scalar.UncheckedScalar($1)"
            );
            
            // 6. Replace SplitText
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+SplitText\\s*\\(([^)]+)\\)", 
                "new org.cactoos.text.SplitText($1)"
            );
            
            // 7. Replace CollectionOf
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+CollectionOf\\s*\\(([^)]+)\\)", 
                "new org.cactoos.collection.CollectionOf($1)"
            );
            
            // 8. Replace IoCheckedScalar
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+IoCheckedScalar\\s*\\(([^)]+)\\)", 
                "new org.cactoos.scalar.IoCheckedScalar($1)"
            );
            
            // 9. Replace SolidScalar
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+SolidScalar\\s*\\(([^)]+)\\)", 
                "new org.cactoos.scalar.SolidScalar($1)"
            );
            
            // 10. Replace JoinedText
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+JoinedText\\s*\\(([^)]+)\\)", 
                "new org.cactoos.text.JoinedText($1)"
            );
            
            // 11. Replace StickyScalar
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+StickyScalar\\s*\\(([^)]+)\\)", 
                "new org.cactoos.scalar.StickyScalar($1)"
            );
            
            // 12. Replace TrimmedText
            modifiedContent = modifiedContent.replaceAll(
                "new\\s+TrimmedText\\s*\\(([^)]+)\\)", 
                "new org.cactoos.text.TrimmedText($1)"
            );
            
            // Write back to file if changes were made
            if (!content.equals(modifiedContent)) {
                Files.write(filePath, modifiedContent.getBytes());
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}