package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation utility to fix breaking changes in acceptance-test-harness APIs.
 * This transformation specifically handles signature changes in AbstractPipelineTest.assertJavadoc method
 * from (Job) to (Job, boolean) - a common breaking change in acceptance-test-harness dependencies.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        try (Stream<Path> paths = Files.walk(sourcePath).filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"))) {
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                fixBreakingChanges(file);
            }
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void fixBreakingChanges(Path filePath) {
        try {
            // Parse the Java file
            FileInputStream in = new FileInputStream(filePath.toFile());
            CompilationUnit cu = StaticJavaParser.parse(in);
            in.close();
            
            // Apply the transformation visitor
            BreakingChangeFixer visitor = new BreakingChangeFixer();
            cu.accept(visitor, null);
            
            // Save the updated file
            LexicalPreservingPrinter.print(cu);
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes breaking changes in acceptance-test-harness APIs, 
     * specifically handling assertJavadoc signature changes
     */
    private static class BreakingChangeFixer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr node, Void arg) {
            // Fix AbstractPipelineTest.assertJavadoc signature change
            // From: assertJavadoc(Job job) 
            // To: assertJavadoc(Job job, boolean checkJavadoc)
            if (node.getScope().isPresent()) {
                // Check for calls to assertJavadoc method with wrong signature
                if (node.getNameAsString().equals("assertJavadoc")) {
                    // If there is exactly 1 argument, add a boolean parameter for checking javadoc
                    if (node.getArguments().size() == 1) {
                        // Add true as second argument to indicate that javadoc should be checked
                        node.addArgument("true");
                    }
                }
            }
            
            return super.visit(node, arg);
        }
    }
}
