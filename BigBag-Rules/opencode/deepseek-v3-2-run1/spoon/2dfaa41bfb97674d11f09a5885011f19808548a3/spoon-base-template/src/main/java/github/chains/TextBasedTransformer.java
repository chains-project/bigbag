package github.chains;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple text-based transformer for Mockito 5.1.1 getArgumentAt → getArgument change.
 * This is more reliable than Spoon for simple text replacements.
 */
public class TextBasedTransformer {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java TextBasedTransformer <sourceDir> <outputDir>");
            System.err.println("  sourceDir: Path to source code directory");
            System.err.println("  outputDir: Path for transformed code");
            System.err.println("\nExample: java TextBasedTransformer ./src ./transformed");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        
        if (!Files.exists(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("=== Mockito 5.1.1 API Migration Tool ===");
        System.out.println("Transforming: getArgumentAt() -> getArgument()");
        System.out.println("Source: " + sourceDir.toAbsolutePath());
        System.out.println("Output: " + outputDir.toAbsolutePath());
        System.out.println();
        
        // Create output directory
        if (Files.exists(outputDir)) {
            deleteDirectory(outputDir);
        }
        Files.createDirectories(outputDir);
        
        List<Path> transformedFiles = new ArrayList<>();
        
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    boolean wasTransformed = processJavaFile(file, sourceDir, outputDir);
                    if (wasTransformed) {
                        transformedFiles.add(file);
                    }
                } else {
                    copyFile(file, sourceDir, outputDir);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(sourceDir)) {
                    Path relative = sourceDir.relativize(dir);
                    Path targetDir = outputDir.resolve(relative);
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("\n=== Summary ===");
        System.out.println("Total files processed: " + transformedFiles.size());
        if (!transformedFiles.isEmpty()) {
            System.out.println("\nTransformed files:");
            for (Path file : transformedFiles) {
                System.out.println("  • " + sourceDir.relativize(file));
            }
            System.out.println("\nTransformation complete!");
            System.out.println("Output written to: " + outputDir.toAbsolutePath());
        } else {
            System.out.println("No getArgumentAt() calls found. Project is already compatible.");
        }
    }
    
    private static boolean processJavaFile(Path javaFile, Path sourceDir, Path outputDir) throws IOException {
        String content = Files.readString(javaFile);
        String original = content;
        
        // Pattern 1: .getArgumentAt( - most common
        content = content.replaceAll("\\.getArgumentAt\\(", ".getArgument(");
        
        // Pattern 2: .getArgumentAt ( - with space before parenthesis
        content = content.replaceAll("\\.getArgumentAt\\s+\\(", ".getArgument(");
        
        // Pattern 3: getArgumentAt( - at start of line (unlikely but possible)
        content = content.replaceAll("(?m)^([ \\t]*)getArgumentAt\\(", "$1getArgument(");
        
        if (!content.equals(original)) {
            Path relative = sourceDir.relativize(javaFile);
            Path targetFile = outputDir.resolve(relative);
            Files.writeString(targetFile, content);
            System.out.println("✓ Transformed: " + relative);
            return true;
        } else {
            copyFile(javaFile, sourceDir, outputDir);
            return false;
        }
    }
    
    private static void copyFile(Path file, Path sourceDir, Path outputDir) throws IOException {
        Path relative = sourceDir.relativize(file);
        Path targetFile = outputDir.resolve(relative);
        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
    
    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}