package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        // Generic transformation for fixing breaking changes in Google API services
        // Specifically targeting the removal of DnsRequestInitializer in google-api-services-dns
        // This pattern can be reused for similar breaking changes
        
        String sourceDirectory = "/workspace/google-cloud-java";
        String targetDirectory = "/workspace/google-cloud-java-fixed";
        
        // Process all Java files in the source directory
        java.nio.file.Files.walk(Paths.get(sourceDirectory))
                .filter(java.nio.file.Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        processJavaFile(path, targetDirectory);
                    } catch (IOException e) {
                        System.err.println("Error processing file " + path + ": " + e.getMessage());
                    }
                });
    }
    
    private static void processJavaFile(Path sourcePath, String targetDirectory) throws IOException {
        // Read the file content
        String content = new String(Files.readAllBytes(sourcePath));
        
        // Parse with JavaParser
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply transformation - find and fix DnsRequestInitializer usage
        boolean modified = false;
        
        // Look for Builder pattern usage with setDnsRequestInitializer
        modified |= fixDnsRequestInitializerUsage(cu);
        
        // If modified, write the file to the target directory
        if (modified) {
            // Create target directory structure
            String relativePath = sourcePath.toString().substring(30);
            Path targetPath = Paths.get(targetDirectory, relativePath);
            Files.createDirectories(targetPath.getParent());
            
            // Write modified content
            Files.write(targetPath, cu.toString().getBytes());
        }
    }
    
    private static boolean fixDnsRequestInitializerUsage(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all method calls that involve setDnsRequestInitializer
        // This is a structural pattern matching approach for the breaking change
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
        for (MethodCallExpr methodCall : methodCalls) {
            if (methodCall.getNameAsString().equals("setDnsRequestInitializer")) {
                // Remove the entire method call - the API has changed and this method no longer exists
                // This is a common breaking change pattern in Google API services
                if (methodCall.getParentNode().isPresent() && 
                    methodCall.getParentNode().get() instanceof ExpressionStmt) {
                    ExpressionStmt stmt = (ExpressionStmt) methodCall.getParentNode().get();
                    stmt.remove();
                    modified = true;
                }
            }
        }
        
        return modified;
    }
}