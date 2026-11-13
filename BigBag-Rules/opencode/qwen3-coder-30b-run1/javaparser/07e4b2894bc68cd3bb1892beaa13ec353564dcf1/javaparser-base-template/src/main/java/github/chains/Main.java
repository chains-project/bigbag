package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Main <sourceDirectory> <outputDirectory>");
            return;
        }

        String sourceDir = args[0];
        String outputDir = args[1];
        
        try {
            // Process all Java files in the source directory
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        processFile(path.toString(), outputDir);
                    } catch (Exception e) {
                        System.err.println("Error processing file " + path + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
    }

    private static void processFile(String filePath, String outputDir) throws IOException {
        // Read the file
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        
        // Parse the Java file
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply the transformation
        new BreakingChangeVisitor().visit(cu, null);
        
        // Write the transformed file to output directory
        String relativePath = filePath.substring(filePath.indexOf("code-coverage-api-plugin") + "code-coverage-api-plugin".length());
        String outputPath = outputDir + relativePath;
        
        // Ensure output directory exists
        Path outputFilePath = Paths.get(outputPath);
        Files.createDirectories(outputFilePath.getParent());
        
        // Write the transformed content
        Files.write(outputFilePath, cu.toString().getBytes());
    }
    
    /**
     * Visitor to identify and fix breaking API changes in the acceptance test harness
     */
    private static class BreakingChangeVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for method calls that might have changed signatures
            String methodName = methodCall.getNameAsString();
            
            // Handle common breaking changes in coverage publisher APIs
            if (methodName.equals("createAdapterPageArea") ||
                methodName.equals("createThresholdsPageArea") ||
                methodName.equals("createGlobalThresholdsPageArea")) {
                
                // Common breaking change: Adding a boolean parameter to methods
                // Check if we have 2 arguments and the second is a StringLiteralExpr
                if (methodCall.getArguments().size() == 2 && 
                    methodCall.getArguments().get(1) instanceof StringLiteralExpr) {
                    // Add a default boolean parameter (false) to maintain compatibility
                    methodCall.addArgument(new BooleanLiteralExpr(false));
                }
            }
        }
    }
}