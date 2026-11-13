package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) {
        // Input and output paths - configurable for reuse
        String inputDir = args.length > 0 ? args[0] : "/workspace/jcabi-github/src/main/java";
        String outputDir = args.length > 1 ? args[1] : "/workspace/jcabi-github-transformed/src/main/java";
        
        try {
            // Process all Java files
            Files.walk(Paths.get(inputDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        processFile(path.toString(), outputDir);
                    } catch (Exception e) {
                        System.err.println("Error processing " + path + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
    }
    
    private static void processFile(String inputPath, String outputDir) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(inputPath)));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply transformations to fix breaking changes in jcabi-aspects
        transformAspectsAnnotations(cu);
        
        // Print the modified code back to file
        Path outputPath = Paths.get(outputDir, inputPath.substring(inputPath.lastIndexOf("/")));
        Files.createDirectories(outputPath.getParent());
        try (FileWriter writer = new FileWriter(outputPath.toString())) {
            writer.write(LexicalPreservingPrinter.print(cu));
        }
    }
    
    /**
     * Generic transformation to fix breaking changes in jcabi-aspects annotations
     * This handles common breaking changes, especially with Loggable annotation
     */
    private static void transformAspectsAnnotations(CompilationUnit cu) {
        // Handle @Loggable with named parameters (level => value)
        cu.findAll(NormalAnnotationExpr.class)
            .stream()
            .filter(annotation -> "Loggable".equals(annotation.getNameAsString()))
            .forEach(annotation -> {
                List<MemberValuePair> pairs = annotation.getPairs();
                for (MemberValuePair pair : pairs) {
                    if ("level".equals(pair.getNameAsString())) {
                        // Change parameter name from "level" to "value"
                        pair.setName("value");
                    }
                }
            });
            
        // Handle @Loggable with single member expressions that might have changed
        cu.findAll(SingleMemberAnnotationExpr.class)
            .stream()
            .filter(annotation -> "Loggable".equals(annotation.getNameAsString()))
            .forEach(annotation -> {
                // This handles cases like @Loggable(Loggable.DEBUG)
                // We might want to normalize them to a consistent form if needed
            });
            
        // Handle @Loggable with empty parameters
        cu.findAll(NormalAnnotationExpr.class)
            .stream()
            .filter(annotation -> "Loggable".equals(annotation.getNameAsString()))
            .filter(annotation -> annotation.getPairs().isEmpty())
            .forEach(annotation -> {
                // Add default value if none specified - assuming DEBUG level (100)
                annotation.addPair("value", new IntegerLiteralExpr("100"));
            });
            
        // Handle Loggable annotation references that might have changed
        cu.findAll(NameExpr.class)
            .stream()
            .filter(name -> name.getNameAsString().contains("Loggable"))
            .forEach(name -> {
                // This is a placeholder for more sophisticated handling
                // of references to Loggable constants that might have changed
            });
    }
}
