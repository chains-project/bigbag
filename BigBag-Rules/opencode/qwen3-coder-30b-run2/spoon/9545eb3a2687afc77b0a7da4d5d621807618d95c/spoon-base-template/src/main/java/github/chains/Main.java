package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

/**
 * Simple utility to fix Hazelcast 5.1 breaking changes by replacing imports
 * from com.hazelcast.core to com.hazelcast.cluster
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path dir = Paths.get(sourceDirectory);
        
        if (!Files.exists(dir)) {
            System.err.println("Directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        try {
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
            
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String modifiedContent = fixImports(content);
            
            if (!content.equals(modifiedContent)) {
                Files.write(filePath, modifiedContent.getBytes());
                System.out.println("Fixed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static String fixImports(String content) {
        // Patterns to match the specific imports that need to be changed
        content = content.replaceAll(
            "import com\\.hazelcast\\.core\\.Cluster;",
            "import com.hazelcast.cluster.Cluster;"
        );
        
        content = content.replaceAll(
            "import com\\.hazelcast\\.core\\.Member;",
            "import com.hazelcast.cluster.Member;"
        );
        
        content = content.replaceAll(
            "import com\\.hazelcast\\.core\\.MembershipEvent;",
            "import com.hazelcast.cluster.MembershipEvent;"
        );
        
        content = content.replaceAll(
            "import com\\.hazelcast\\.core\\.MembershipListener;",
            "import com.hazelcast.cluster.MembershipListener;"
        );
        
        content = content.replaceAll(
            "import com\\.hazelcast\\.core\\.MemberAttributeEvent;",
            "import com.hazelcast.cluster.MemberAttributeEvent;"
        );
        
        return content;
    }
}