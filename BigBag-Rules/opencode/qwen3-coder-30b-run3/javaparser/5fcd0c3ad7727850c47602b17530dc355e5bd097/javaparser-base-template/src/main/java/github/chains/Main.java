package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try {
            // Process all Java files in the directory recursively
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to fix CoverageDatabase.getClassInfo calls
            new CoverageDatabaseFixVisitor().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes calls to deprecated CoverageDatabase.getClassInfo()
     * This transforms calls from:
     * coverage.getClassInfo(someSet)
     * to:
     * Collections.emptySet()
     */
    private static class CoverageDatabaseFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Match calls to getClassInfo method
            if (methodCall.getNameAsString().equals("getClassInfo")) {
                try {
                    // Replace with Collections.emptySet() as a safe fallback
                    // This avoids the NoSuchMethodError but will need to be
                    // customized further based on actual usage context
                    
                    // Create the replacement expression: Collections.emptySet()
                    MethodCallExpr replacement = new MethodCallExpr(
                        new NameExpr("Collections"),
                        "emptySet"
                    );
                    
                    // Replace the entire method call with Collections.emptySet()
                    methodCall.replace(replacement);
                } catch (Exception e) {
                    // If we can't create the replacement, leave it alone
                }
            }
        }
    }
}
