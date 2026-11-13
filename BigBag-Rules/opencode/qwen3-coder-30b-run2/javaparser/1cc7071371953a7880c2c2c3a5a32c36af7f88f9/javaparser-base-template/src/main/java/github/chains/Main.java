package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation to remove all references to org.assertj.core.internal.bytebuddy packages
 * This fixes breaking changes in AssertJ 3.23.0 where bytebuddy dependencies were removed from core
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path dir = Paths.get(sourceDirectory);
        
        // Find all Java files in the directory
        try (Stream<Path> javaFiles = Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFilePaths = javaFiles.collect(Collectors.toList());
            
            for (Path javaFile : javaFilePaths) {
                removeByteBuddyReferences(javaFile);
            }
            
            System.out.println("Transformation complete. Processed " + javaFilePaths.size() + " Java files.");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void removeByteBuddyReferences(Path javaFile) {
        try {
            String content = new String(Files.readAllBytes(javaFile));
            
            // Remove imports that reference bytebuddy packages
            String modifiedContent = content.replaceAll(
                "(?m)^\\s*import\\s+org\\.assertj\\.core\\.internal\\.bytebuddy.*;\\s*$",
                ""
            );
            
            // Remove any remaining references to bytebuddy classes in the code
            modifiedContent = modifiedContent.replaceAll(
                "\\s+org\\.assertj\\.core\\.internal\\.bytebuddy\\.[\\w\\.]+",
                ""
            );
            
            // Remove any remaining static imports
            modifiedContent = modifiedContent.replaceAll(
                "(?m)^\\s*static\\s+import\\s+org\\.assertj\\.core\\.internal\\.bytebuddy\\..*;\\s*$",
                ""
            );
            
            // Remove class references like ByteBuddy, Implementation, etc.
            modifiedContent = modifiedContent.replaceAll(
                "\\b(ByteBuddy|Implementation|TypeCache|SimpleKey|ClassLoadingStrategy|MethodDelegation|AuxiliaryType|RuntimeType|This|SuperCall|TypeValidation|ElementMatchers|ClassInjector)\\b",
                ""
            );
            
            if (!content.equals(modifiedContent)) {
                Files.write(javaFile, modifiedContent.getBytes());
                System.out.println("Modified file: " + javaFile);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
}