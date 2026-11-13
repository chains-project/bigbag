/*
 * This is a simple transformation to fix jasypt dependency breakage.
 * It replaces:
 *  - org.jasypt.spring.security.PasswordEncoder with org.jasypt.util.password.StrongPasswordEncryptor
 *  - org.jasypt.spring.security.PBEPasswordEncoder with org.jasypt.encryption.pbe.StandardPBEStringEncryptor
 *  - Removes deprecated imports
 *  - Replaces references to jasypt classes with modern equivalents
 */

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class FixJasyptDependency {
    
    public static void main(String[] args) throws IOException {
        Path sourceDir = Paths.get("/workspace/geostore");
        if (args.length > 0) {
            sourceDir = Paths.get(args[0]);
        }
        
        // Process all Java files recursively
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(FixJasyptDependency::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            boolean modified = false;
            
            // Remove deprecated imports
            content = content.replaceAll(
                "import\\s+org\\.jasypt\\.spring\\.security\\.PasswordEncoder;", 
                "");
            content = content.replaceAll(
                "import\\s+org\\.jasypt\\.spring\\.security\\.PBEPasswordEncoder;", 
                "");
            
            // Remove acegi import which is also incompatible
            content = content.replaceAll(
                "import\\s+org\\.acegisecurity\\.providers\\.encoding\\.PasswordEncoder;", 
                "");
            
            // Replace PasswordEncoder instantiation
            content = content.replaceAll(
                "\\borg\\.jasypt\\.spring\\.security\\.PasswordEncoder\\b",
                "org.jasypt.util.password.StrongPasswordEncryptor");
            
            // Handle PasswordEncoder constructor calls
            content = content.replaceAll(
                "new\\s+PasswordEncoder\\s*\\(", 
                "new org.jasypt.util.password.StrongPasswordEncryptor(");
            
            // Replace PBEPasswordEncoder instantiation  
            content = content.replaceAll(
                "\\borg\\.jasypt\\.spring\\.security\\.PBEPasswordEncoder\\b",
                "org.jasypt.encryption.pbe.StandardPBEStringEncryptor");
            
            // Handle PBEPasswordEncoder constructor calls
            content = content.replaceAll(
                "new\\s+PBEPasswordEncoder\\s*\\(", 
                "new org.jasypt.encryption.pbe.StandardPBEStringEncryptor(");
            
            // Replace references to StrongPasswordEncryptor with correct class name
            content = content.replaceAll(
                "org\\.jasypt\\.util\\.password\\.StrongPasswordEncryptor",
                "org.jasypt.util.password.StrongPasswordEncryptor");
            
            // Special handling for GeoStoreDigestPasswordEncoder to fix the inheritance
            if (filePath.endsWith("GeoStoreDigestPasswordEncoder.java")) {
                // Replace the method that was using PasswordEncoder
                content = content.replaceAll(
                    "protected PasswordEncoder createStringEncoder\\(\\)",
                    "protected org.jasypt.util.password.StrongPasswordEncryptor createStringEncoder()");
            }
            
            // Special handling for GeoStorePBEPasswordEncoder to fix the inheritance
            if (filePath.endsWith("GeoStorePBEPasswordEncoder.java")) {
                // Replace the method that was using PBEPasswordEncoder
                content = content.replaceAll(
                    "protected PasswordEncoder createStringEncoder\\(\\)",
                    "protected org.jasypt.encryption.pbe.StandardPBEStringEncryptor createStringEncoder()");
            }
            
            // Write back if modified
            if (!content.equals(Files.readString(filePath))) {
                Files.writeString(filePath, content);
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}