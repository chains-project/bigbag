package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Generic transformation rule for fixing breaking changes in com.artipie:http v1.2.1
 * 
 * Breaking Changes Fixed:
 * 1. Authentication.User inner class → AuthUser top-level class
 * 2. AuthUser constructor: User(String) → AuthUser(String, String)  
 * 3. Authentication.user() return: Optional<User> → Optional<AuthUser>
 * 
 * This transformation is generic and reusable for any project affected by these changes.
 * Simply run: java github.chains.Main <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying generic transformation for http v1.2.1 breaking changes...");
        System.out.println("Source directory: " + sourceDir);
        
        try {
            transformDirectory(Paths.get(sourceDir));
            System.out.println("Transformation complete!");
            System.out.println("Note: Check for any remaining compilation errors related to other dependencies.");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path file) {
        try {
            String content = Files.readString(file);
            String original = content;
            boolean changed = false;
            
            // 1. Fix imports: Authentication.User → AuthUser
            if (content.contains("import com.artipie.http.auth.Authentication.User;")) {
                content = content.replace(
                    "import com.artipie.http.auth.Authentication.User;",
                    "import com.artipie.http.auth.AuthUser;"
                );
                changed = true;
                System.out.println("  Fixed import in: " + file.getFileName());
            }
            
            // 2. Replace type references: Authentication.User → AuthUser
            if (content.contains("Authentication.User")) {
                content = content.replace("Authentication.User", "AuthUser");
                changed = true;
                System.out.println("  Fixed type references in: " + file.getFileName());
            }
            
            // 3. Replace constructor calls: new Authentication.User(name) → new AuthUser(name, name)
            // Handle double quotes
            String regexDoubleQuotes = "new AuthUser\\(\"([^\"]+)\"\\)";
            if (content.matches(".*" + regexDoubleQuotes + ".*")) {
                content = content.replaceAll(regexDoubleQuotes, "new AuthUser(\"$1\", \"$1\")");
                changed = true;
                System.out.println("  Fixed AuthUser constructors (double quotes) in: " + file.getFileName());
            }
            
            // Handle single quotes  
            String regexSingleQuotes = "new AuthUser\\('([^']+)'\\)";
            if (content.matches(".*" + regexSingleQuotes + ".*")) {
                content = content.replaceAll(regexSingleQuotes, "new AuthUser('$1', '$1')");
                changed = true;
                System.out.println("  Fixed AuthUser constructors (single quotes) in: " + file.getFileName());
            }
            
            // 4. Also handle the case where we still have Authentication.User constructor calls
            // (if the type reference replacement happened after constructor pattern)
            if (content.contains("new Authentication.User(")) {
                content = content.replace("new Authentication.User(", "new AuthUser(");
                changed = true;
                System.out.println("  Fixed remaining Authentication.User constructors in: " + file.getFileName());
            }
            
            // 5. Fix TestAuthUser references if they exist (custom test classes)
            if (content.contains("TestAuthUser")) {
                content = content.replace("TestAuthUser", "AuthUser");
                changed = true;
                System.out.println("  Fixed TestAuthUser references in: " + file.getFileName());
            }
            
            if (changed) {
                Files.writeString(file, content);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
}