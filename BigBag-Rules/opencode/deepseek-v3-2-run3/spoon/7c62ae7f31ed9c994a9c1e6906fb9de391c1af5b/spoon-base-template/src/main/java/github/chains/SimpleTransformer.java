package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple file transformer for Hibernate UserType API breaking change.
 * This is a simpler alternative to Spoon transformation that directly modifies files.
 * 
 * Breaking Change: Hibernate 5.x changed UserType interface method signatures:
 * - Old: nullSafeGet(ResultSet, String[], SessionImplementor, Object)
 * - New: nullSafeGet(ResultSet, String[], SharedSessionContractImplementor, Object)
 * - Same change for nullSafeSet method
 */
public class SimpleTransformer {
    
    // Old type to replace
    private static final String OLD_TYPE = "SessionImplementor";
    private static final String OLD_TYPE_FULL = "org.hibernate.engine.spi.SessionImplementor";
    
    // New type to use
    private static final String NEW_TYPE = "SharedSessionContractImplementor";
    private static final String NEW_TYPE_FULL = "org.hibernate.engine.spi.SharedSessionContractImplementor";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleTransformer <source-file-or-directory>");
            System.err.println("Example: java SimpleTransformer /path/to/ServiceDateUserType.java");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        File file = new File(sourcePath);
        
        if (file.isDirectory()) {
            processDirectory(file);
        } else {
            processFile(file.toPath());
        }
        
        System.out.println("Transformation completed!");
    }
    
    private static void processDirectory(File directory) throws IOException {
        System.out.println("Processing directory: " + directory.getAbsolutePath());
        
        List<Path> javaFiles = Files.walk(directory.toPath())
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            processFile(javaFile);
        }
    }
    
    private static void processFile(Path filePath) throws IOException {
        System.out.println("Processing file: " + filePath);
        
        String content = Files.readString(filePath);
        String originalContent = content;
        
        // Check if this file implements UserType
        if (content.contains("implements UserType") || content.contains("implements org.hibernate.usertype.UserType")) {
            System.out.println("  Found UserType implementation");
            
            // Replace imports
            content = content.replace(
                "import " + OLD_TYPE_FULL + ";",
                "import " + NEW_TYPE_FULL + ";"
            );
            
            // Replace method parameter types (simple name)
            content = content.replace(
                "nullSafeGet(ResultSet rs, String[] names, " + OLD_TYPE + " sessionImplementor, Object owner)",
                "nullSafeGet(ResultSet rs, String[] names, " + NEW_TYPE + " sessionImplementor, Object owner)"
            );
            
            content = content.replace(
                "nullSafeSet(PreparedStatement st, Object value, int index, " + OLD_TYPE + " sessionImplementor)",
                "nullSafeSet(PreparedStatement st, Object value, int index, " + NEW_TYPE + " sessionImplementor)"
            );
            
            // Also check for fully qualified type in method signatures
            content = content.replace(
                "nullSafeGet(ResultSet rs, String[] names, " + OLD_TYPE_FULL + " sessionImplementor, Object owner)",
                "nullSafeGet(ResultSet rs, String[] names, " + NEW_TYPE_FULL + " sessionImplementor, Object owner)"
            );
            
            content = content.replace(
                "nullSafeSet(PreparedStatement st, Object value, int index, " + OLD_TYPE_FULL + " sessionImplementor)",
                "nullSafeSet(PreparedStatement st, Object value, int index, " + NEW_TYPE_FULL + " sessionImplementor)"
            );
            
            // Replace parameter declarations in method bodies
            content = content.replace(
                "(" + OLD_TYPE + " sessionImplementor)",
                "(" + NEW_TYPE + " sessionImplementor)"
            );
            
            content = content.replace(
                "(" + OLD_TYPE_FULL + " sessionImplementor)",
                "(" + NEW_TYPE_FULL + " sessionImplementor)"
            );
            
            // If content was modified, write it back
            if (!content.equals(originalContent)) {
                Files.writeString(filePath, content);
                System.out.println("  File updated successfully");
            } else {
                System.out.println("  No changes needed");
            }
        }
    }
}