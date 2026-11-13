package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jakarta EE migration transformation to: " + sourceDir);
        
        Path start = Paths.get(sourceDir);
        int importChanges = 0;
        int fileChanges = 0;
        
        // Walk through all Java files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(start, "**/*.java")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                    boolean fileModified = false;
                    List<String> lines = Files.readAllLines(file);
                    List<String> newLines = new ArrayList<>();
                    
                    for (String line : lines) {
                        String originalLine = line;
                        
                        // Transform import statements
                        if (line.trim().startsWith("import javax.jws")) {
                            line = line.replace("javax.jws", "jakarta.jws");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        } else if (line.trim().startsWith("import javax.jws.soap")) {
                            line = line.replace("javax.jws.soap", "jakarta.jws.soap");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        } else if (line.trim().startsWith("import javax.xml.ws")) {
                            line = line.replace("javax.xml.ws", "jakarta.xml.ws");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        } else if (line.trim().startsWith("import javax.xml.bind")) {
                            line = line.replace("javax.xml.bind", "jakarta.xml.bind");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        } else if (line.trim().startsWith("import javax.xml.datatype")) {
                            line = line.replace("javax.xml.datatype", "jakarta.xml.datatype");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        }
                        
                        newLines.add(line);
                    }
                    
                    if (fileModified) {
                        Files.write(file, newLines);
                        fileChanges++;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            // Fall back to recursive search
            processDirectoryRecursive(start, importChanges, fileChanges);
        }
        
        System.out.println("\nTransformation complete!");
        System.out.println("Total import statements transformed: " + importChanges);
        System.out.println("Total files modified: " + fileChanges);
    }
    
    private static void processDirectoryRecursive(Path dir, int importChanges, int fileChanges) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    processDirectoryRecursive(entry, importChanges, fileChanges);
                } else if (entry.toString().endsWith(".java")) {
                    boolean fileModified = false;
                    List<String> lines = Files.readAllLines(entry);
                    List<String> newLines = new ArrayList<>();
                    
                    for (String line : lines) {
                        String originalLine = line;
                        
                        // Transform import statements
                        if (line.trim().startsWith("import javax.jws")) {
                            line = line.replace("javax.jws", "jakarta.jws");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        } else if (line.trim().startsWith("import javax.jws.soap")) {
                            line = line.replace("javax.jws.soap", "jakarta.jws.soap");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        } else if (line.trim().startsWith("import javax.xml.ws")) {
                            line = line.replace("javax.xml.ws", "jakarta.xml.ws");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        } else if (line.trim().startsWith("import javax.xml.bind")) {
                            line = line.replace("javax.xml.bind", "jakarta.xml.bind");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        } else if (line.trim().startsWith("import javax.xml.datatype")) {
                            line = line.replace("javax.xml.datatype", "jakarta.xml.datatype");
                            importChanges++;
                            fileModified = true;
                            System.out.println("Transformed: " + originalLine.trim() + " -> " + line.trim());
                        }
                        
                        newLines.add(line);
                    }
                    
                    if (fileModified) {
                        Files.write(entry, newLines);
                        fileChanges++;
                    }
                }
            }
        }
    }
}