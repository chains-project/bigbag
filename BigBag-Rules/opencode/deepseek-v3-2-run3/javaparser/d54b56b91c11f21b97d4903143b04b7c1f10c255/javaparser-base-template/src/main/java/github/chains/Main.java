package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            List<Path> javaFiles = findAllJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findAllJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        String originalContent = content;
        
        // Pattern 1: Replace PublishMetadata imports with MessageMetadata
        // Match: import com.google.cloud.pubsublite.PublishMetadata;
        content = content.replace("import com.google.cloud.pubsublite.PublishMetadata;", 
                                  "import com.google.cloud.pubsublite.MessageMetadata;");
        
        // Pattern 2: Replace PublishMetadata type references with MessageMetadata
        // This is a bit risky but should work for this specific case
        // We use word boundaries to avoid partial matches
        content = Pattern.compile("\\bPublishMetadata\\b").matcher(content)
            .replaceAll("MessageMetadata");
        
        // Pattern 3: Remove .setContext(PubsubContext.of(...)) calls
        // Match: .setContext(PubsubContext.of(FRAMEWORK))
        // This handles common variations with whitespace
        content = content.replaceAll("\\.\\s*setContext\\s*\\(\\s*PubsubContext\\.of\\s*\\([^)]*\\)\\s*\\)", "");
        
        if (!content.equals(originalContent)) {
            Files.write(javaFile, content.getBytes());
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }
}