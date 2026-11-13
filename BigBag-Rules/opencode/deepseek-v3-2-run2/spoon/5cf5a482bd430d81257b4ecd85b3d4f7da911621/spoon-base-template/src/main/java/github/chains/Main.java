package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming Java EE (javax) to Jakarta EE (jakarta)...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            Path sourcePath = Paths.get(sourceDir);
            Path outputPath = Paths.get(outputDir);
            
            // Create output directory
            Files.createDirectories(outputPath);
            
            // Track transformations
            Set<String> transformedTypes = Collections.synchronizedSet(new HashSet<>());
            AtomicInteger fileCount = new AtomicInteger(0);
            AtomicInteger importCount = new AtomicInteger(0);
            
            // Walk through all Java files
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".java")) {
                        fileCount.incrementAndGet();
                        List<String> lines = Files.readAllLines(file);
                        List<String> transformedLines = new ArrayList<>();
                        boolean fileChanged = false;
                        
                        for (String line : lines) {
                            String originalLine = line;
                            String transformedLine = line;
                            
                            // Transform import statements
                            if (line.trim().startsWith("import ") || line.trim().startsWith("import static ")) {
                                if (line.contains("javax.")) {
                                    transformedLine = line.replace("javax.", "jakarta.");
                                    if (!transformedLine.equals(originalLine)) {
                                        importCount.incrementAndGet();
                                        fileChanged = true;
                                        
                                        // Extract type name for tracking
                                        String typeName = extractTypeNameFromImport(originalLine);
                                        if (typeName != null && typeName.startsWith("javax.")) {
                                            transformedTypes.add(typeName);
                                        }
                                    }
                                }
                            }
                            
                            // Transform fully qualified type references in code
                            // Pattern to match javax. packages in code (not in comments or strings)
                            transformedLine = transformTypeReferencesInLine(transformedLine, transformedTypes);
                            if (!transformedLine.equals(originalLine)) {
                                fileChanged = true;
                            }
                            
                            transformedLines.add(transformedLine);
                        }
                        
                        // Write transformed file to output directory
                        Path relativePath = sourcePath.relativize(file);
                        Path outputFile = outputPath.resolve(relativePath);
                        Files.createDirectories(outputFile.getParent());
                        Files.write(outputFile, transformedLines);
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // Create corresponding directory in output
                    if (!dir.equals(sourcePath)) {
                        Path relativeDir = sourcePath.relativize(dir);
                        Path outputDirPath = outputPath.resolve(relativeDir);
                        Files.createDirectories(outputDirPath);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            // Output transformation report
            System.out.println("\nTransformation Summary:");
            System.out.println("Total Java files processed: " + fileCount.get());
            System.out.println("Total javax.* imports transformed: " + importCount.get());
            System.out.println("Unique javax.* types transformed: " + transformedTypes.size());
            
            if (!transformedTypes.isEmpty()) {
                System.out.println("\nTransformed types:");
                List<String> sortedTypes = new ArrayList<>(transformedTypes);
                sortedTypes.sort(null);
                for (String type : sortedTypes) {
                    System.out.println("  " + type + " -> " + type.replaceFirst("^javax\\.", "jakarta."));
                }
            }
            
            System.out.println("Transformation complete!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static String transformTypeReferencesInLine(String line, Set<String> transformedTypes) {
        // Simple regex to find javax. package references in code
        // This is a simplified approach - a real implementation would need to parse the Java code properly
        // to avoid replacing javax in comments, strings, or other contexts
        
        // Pattern: javax. followed by identifier characters (letters, digits, dots, $)
        Pattern pattern = Pattern.compile("\\b(javax\\.(?:[a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*)\\b");
        Matcher matcher = pattern.matcher(line);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String javaxType = matcher.group(1);
            String jakartaType = javaxType.replaceFirst("^javax\\.", "jakarta.");
            matcher.appendReplacement(result, jakartaType);
            transformedTypes.add(javaxType);
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    private static String extractTypeNameFromImport(String importStr) {
        // Extract type name from import statement
        if (importStr.trim().startsWith("import ")) {
            String withoutImport = importStr.trim().substring("import ".length());
            if (withoutImport.startsWith("static ")) {
                withoutImport = withoutImport.substring("static ".length());
            }
            // Remove trailing semicolon and any comments
            int semicolonIndex = withoutImport.indexOf(';');
            if (semicolonIndex != -1) {
                withoutImport = withoutImport.substring(0, semicolonIndex);
            }
            return withoutImport.trim();
        }
        return null;
    }
}