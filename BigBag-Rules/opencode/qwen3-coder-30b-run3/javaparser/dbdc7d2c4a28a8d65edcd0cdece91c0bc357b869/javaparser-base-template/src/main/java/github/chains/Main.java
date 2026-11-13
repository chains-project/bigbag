package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Generic transformation to remove enableLogging method calls from plexus-archiver
 * This addresses the breaking change in plexus-archiver 4.4.0 where enableLogging was removed
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Find and remove enableLogging method calls
            boolean modified = removeEnableLoggingCalls(cu);
            
            // If the file was modified, save it back
            if (modified) {
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                    System.out.println("Modified: " + filePath);
                } catch (IOException e) {
                    System.err.println("Failed to write file " + filePath + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to parse file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean removeEnableLoggingCalls(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all method calls in the compilation unit
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
        
        for (MethodCallExpr methodCall : methodCalls) {
            // Check if this is a call to enableLogging method
            if ("enableLogging".equals(methodCall.getNameAsString())) {
                // Get the statement containing this method call
                Optional<ExpressionStmt> statementOpt = methodCall.findAncestor(ExpressionStmt.class);
                
                if (statementOpt.isPresent()) {
                    ExpressionStmt statement = statementOpt.get();
                    
                    // Remove the entire statement (the method call line)
                    statement.remove();
                    modified = true;
                }
            }
        }
        
        return modified;
    }
}