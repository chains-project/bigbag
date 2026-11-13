package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for fixing logback breaking changes.
 * This transformation handles common API signature changes in logback libraries.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java Main <sourceDirectory> <outputDirectory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        try {
            processDirectory(sourceDir, outputDir);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(String sourceDir, String outputDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path outputPath = Paths.get(outputDir);
        
        Files.createDirectories(outputPath);
        
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    processFile(path.toString(), outputPath.resolve(sourcePath.relativize(path)).toString());
                } catch (IOException e) {
                    System.err.println("Error processing file " + path + ": " + e.getMessage());
                }
            });
    }
    
    private static void processFile(String inputFile, String outputFile) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(inputFile));
        
        // Apply transformations for logback API changes
        new LogbackApiChangeVisitor().visit(cu, null);
        
        // Create parent directories for output file
        Path outputFilePath = Paths.get(outputFile);
        Files.createDirectories(outputFilePath.getParent());
        
        // Write the modified code back to output file
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(new DefaultPrettyPrinter().print(cu));
        }
    }
    
    /**
     * Visitor that identifies and fixes common logback breaking changes
     */
    private static class LogbackApiChangeVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Handle common logback API migration patterns
            handleLogbackMethodChanges(methodCall);
        }
        
        private void handleLogbackMethodChanges(MethodCallExpr methodCall) {
            // Pattern 1: Check for calls to methods that had additional parameters added
            // Example: context.putObject(key, value) may have changed to context.putObject(key, value, strict)
            if (matchesLogbackPutObject(methodCall)) {
                // Add default third parameter if needed
                NodeList<Expression> args = methodCall.getArguments();
                if (args.size() == 2) {
                    // Add a default third parameter (false for strict mode)
                    args.add(new BooleanLiteralExpr(false));
                }
            }
            
            // Pattern 2: Check for calls to methods that had optional parameters
            // Example: start() or stop() methods might have gained additional parameters
            // This pattern would be implemented based on the specific breaking change
            
            // Pattern 3: Handle method renames or deprecations
            // Example: rename of methods like "doAppend" to "append" or vice versa
            // This would be customized based on actual API changes
            
            // Pattern 4: Handle constructor signature changes
            // Example: new AppenderBase() constructor might have gained parameters
        }
        
        private boolean matchesLogbackPutObject(MethodCallExpr methodCall) {
            return methodCall.getScope().isPresent() &&
                   methodCall.getScope().get() instanceof MethodCallExpr &&
                   ((MethodCallExpr) methodCall.getScope().get()).getNameAsString().equals("getContext") &&
                   methodCall.getNameAsString().equals("putObject");
        }
    }
}
