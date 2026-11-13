package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Transforms Java files to fix logback-classic 1.4.1 compatibility with SLF4J 1.x");
            System.err.println("Problem: ch.qos.logback.classic.Logger implements org.slf4j.spi.LoggingEventAware");
            System.err.println("which is an SLF4J 2.x interface not present in SLF4J 1.x");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        System.out.println("Fixing logback 1.4.1 + SLF4J 1.x compatibility issue...");
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws IOException {
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        int transformedCount = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedCount++;
            }
        }
        
        System.out.println("Transformed " + transformedCount + " files with logback Logger usage.");
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
             .filter(Files::isRegularFile)
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        JavaParser javaParser = new JavaParser();
        CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse: " + javaFile)
        );
        
        // Apply transformation
        CompilationUnit transformedCu = FinalTransformation.transform(cu);
        
        // Check if file was modified
        if (!cu.equals(transformedCu)) {
            // Write the transformed file
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String transformedCode = printer.print(transformedCu);
            
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(transformedCode);
            }
            
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }
}