package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithExpression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to replace TestListResolver.getWildcard() calls
            cu.accept(new TestListResolverVisitor(), null);
            
            // Save the modified file
            java.nio.file.Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to find and replace TestListResolver.getWildcard() calls
     */
    private static class TestListResolverVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Match calls to TestListResolver.getWildcard()
            Optional<NameExpr> qualifier = methodCall.getScope().filter(NameExpr.class::isInstance)
                    .map(NameExpr.class::cast);
            
            if (qualifier.isPresent() && "TestListResolver".equals(qualifier.get().getNameAsString())) {
                if ("getWildcard".equals(methodCall.getNameAsString())) {
                    // Replace with a suitable wildcard pattern
                    // This is a generic fix for breaking changes in Maven Surefire API
                    methodCall.setArguments(new NodeList<>(new StringLiteralExpr("**/*")));
                }
            }
        }
    }
}
