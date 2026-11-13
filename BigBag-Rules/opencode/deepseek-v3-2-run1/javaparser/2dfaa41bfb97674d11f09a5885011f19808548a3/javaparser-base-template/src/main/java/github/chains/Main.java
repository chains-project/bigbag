package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    public static class MockitoGetArgumentAtTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a method call to getArgumentAt
            if (n.getNameAsString().equals("getArgumentAt")) {
                // Check if we have exactly 2 arguments (int, Class)
                if (n.getArguments().size() == 2) {
                    // Create the new method call with same arguments but name changed to getArgument
                    MethodCallExpr newCall = new MethodCallExpr(n.getScope().orElse(null), "getArgument");
                    newCall.setArguments(n.getArguments());
                    newCall.setTypeArguments(n.getTypeArguments().orElse(null));
                    newCall.setComment(n.getComment().orElse(null));
                    
                    // Copy any line/column information if available
                    if (n.getRange().isPresent()) {
                        newCall.setRange(n.getRange().get());
                    }
                    
                    return newCall;
                }
            }
            
            // For any other method call, continue with default behavior
            return super.visit(n, arg);
        }
    }
    
    public static List<Path> findJavaFiles(Path startDir) throws Exception {
        return Files.walk(startDir)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
    }
    
    public static void transformFile(Path filePath) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + filePath)
        );
        
        // Apply transformation
        MockitoGetArgumentAtTransformer transformer = new MockitoGetArgumentAtTransformer();
        cu.accept(transformer, null);
        
        // Write back the transformed file
        PrinterConfiguration config = new DefaultPrinterConfiguration();
        
        String transformedCode = new DefaultPrettyPrinter(config).print(cu);
        Files.write(filePath, transformedCode.getBytes());
        
        System.out.println("Transformed: " + filePath);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("This tool transforms Mockito getArgumentAt() calls to getArgument()");
            System.err.println("to fix compatibility with Mockito 5.x");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            System.out.println("Scanning for Java files in: " + sourceDir);
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedCount = 0;
            for (Path javaFile : javaFiles) {
                try {
                    // Read file to check if it contains getArgumentAt
                    String content = Files.readString(javaFile);
                    if (content.contains("getArgumentAt")) {
                        transformFile(javaFile);
                        transformedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("Transformation complete. Modified " + transformedCount + " files.");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}