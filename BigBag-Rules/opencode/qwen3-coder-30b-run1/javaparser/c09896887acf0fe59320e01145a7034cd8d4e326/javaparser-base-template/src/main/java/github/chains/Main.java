package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        // Check if source directory is provided
        String sourceDir = args.length > 0 ? args[0] : "/workspace/incrementals-tools";
        
        Path projectRoot = Paths.get(sourceDir);
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                processJavaFile(javaFile);
            }
        }
    }

    private static void processJavaFile(Path javaFile) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Use a visitor to find and fix the specific pattern
        new GHCompareStatusFixVisitor().visit(cu, null);
        
        // If changes were made, write back to file
        String newContent = cu.toString();
        if (!newContent.equals(content)) {
            Files.write(javaFile, newContent.getBytes());
            System.out.println("Modified: " + javaFile);
        }
    }
    
    private static class GHCompareStatusFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is accessing .status on a GHCompare object
            if (n.getNameAsString().equals("status")) {
                // Check if the base expression is a method call to getCompare
                if (n.getScope() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) n.getScope();
                    if (methodCall.getNameAsString().equals("getCompare")) {
                        // This is the pattern we want to fix: .getCompare(...).status
                        // Replace with: .getCompare(...).getStatus()
                        
                        // Create the new method call expression
                        MethodCallExpr newMethodCall = new MethodCallExpr(
                            methodCall.clone(), 
                            "getStatus"
                        );
                        
                        // Replace the field access with the method call
                        n.replace(newMethodCall);
                    }
                }
            }
        }
    }
}