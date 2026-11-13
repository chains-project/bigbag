package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule for detecting MINA 2.2.1 breaking changes.
 * Specifically identifies usage patterns that would break due to API changes in IoUtil.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);

        System.out.println("Analyzing project for MINA 2.2.1 breaking changes...");
        System.out.println("Looking for potential IoUtil.await() method usage that might be affected by API changes.");
        System.out.println();

        // Process all Java files in the directory recursively
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
        System.out.println("Analysis complete. Check for IoUtil.await() usage patterns in reported files.");
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the IoUtil detection
            IoUtilDetector detector = new IoUtilDetector();
            detector.visit(cu, null);
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    /**
     * Visitor that detects IoUtil usage that may be affected by MINA 2.2.1 changes
     */
    private static class IoUtilDetector extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for calls to methods on IoUtil class
            if (methodCall.getScope().isPresent()) {
                String scopeStr = methodCall.getScope().get().toString();
                if (scopeStr.equals("IoUtil")) {
                    String methodName = methodCall.getNameAsString();
                    
                    // Report any IoUtil method calls for manual review
                    System.out.println("IoUtil." + methodName + " call found in: " + 
                        methodCall.getRange().orElseThrow().begin.line + 
                        " - " + methodCall.toString());
                }
            }
        }
    }
}