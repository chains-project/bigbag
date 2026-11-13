package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class SimpleTextTransformer {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java SimpleTextTransformer <sourceDir> <outputDir>");
            System.err.println("  sourceDir: Path to the source code directory to transform");
            System.err.println("  outputDir: Path where transformed code will be written");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        if (!Files.exists(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Create output directory if it doesn't exist
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        List<Path> transformedFiles = new ArrayList<>();
        
        // Walk through all Java files
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    transformJavaFile(file, sourceDir, outputDir, transformedFiles);
                } else {
                    // Copy non-Java files as-is
                    Path relativePath = sourceDir.relativize(file);
                    Path targetPath = outputDir.resolve(relativePath);
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Create corresponding directory in output
                Path relativeDir = sourceDir.relativize(dir);
                Path targetDir = outputDir.resolve(relativeDir);
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("Transformation complete!");
        System.out.println("Transformed " + transformedFiles.size() + " files:");
        for (Path file : transformedFiles) {
            System.out.println("  " + sourceDir.relativize(file));
        }
    }
    
    private static void transformJavaFile(Path javaFile, Path sourceDir, Path outputDir, 
                                         List<Path> transformedFiles) throws IOException {
        String content = Files.readString(javaFile);
        String originalContent = content;
        
        // Pattern 1: invocation.getArgumentAt(index, Class.class)
        // Pattern 2: invocation.getArgumentAt(index, SomeClass.class)
        // We need to be careful to only replace getArgumentAt when it's a method call
        // and not part of a larger identifier
        
        // Simple regex replacement that's good enough for this case
        // Matches: .getArgumentAt( followed by any characters that are not )
        // This will catch most cases
        String transformed = content.replaceAll("(\\.)getArgumentAt\\(", "$1getArgument(");
        
        // Also handle cases with whitespace: .getArgumentAt (
        transformed = transformed.replaceAll("(\\.)getArgumentAt\\s+\\(", "$1getArgument(");
        
        if (!transformed.equals(originalContent)) {
            transformedFiles.add(javaFile);
            
            // Write transformed content
            Path relativePath = sourceDir.relativize(javaFile);
            Path targetPath = outputDir.resolve(relativePath);
            Files.writeString(targetPath, transformed);
            
            System.out.println("Transformed: " + relativePath);
        } else {
            // Copy unchanged file
            Path relativePath = sourceDir.relativize(javaFile);
            Path targetPath = outputDir.resolve(relativePath);
            Files.copy(javaFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}