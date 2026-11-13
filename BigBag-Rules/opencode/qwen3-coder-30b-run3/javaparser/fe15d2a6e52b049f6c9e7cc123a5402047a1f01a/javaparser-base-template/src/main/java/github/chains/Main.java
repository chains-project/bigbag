package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix calls to removed getInstance() methods 
 * in jaxb2-basics-runtime 1.11.1 dependency.
 * 
 * This fixes compilation errors in projects that used the old jaxb2-basics-runtime
 * API where calls to getInstance() methods were removed.
 */
public class Main {
    public static void main(String[] args) {
        // Process all Java files recursively in the project directory
        String projectPath = args.length > 0 ? args[0] : "/workspace/billy";
        
        try {
            processJavaFiles(projectPath);
            System.out.println("Transformation complete.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFiles(String projectPath) throws IOException {
        Path rootPath = Paths.get(projectPath);
        Files.walk(rootPath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix getInstance() calls
            new GetInstanceFixerVisitor().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that finds and fixes calls to getInstance() methods on strategy classes
     */
    private static class GetInstanceFixerVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a call to getInstance() method
            if (methodCall.getNameAsString().equals("getInstance")) {
                // Check if it's a call on a strategy class
                methodCall.getScope().ifPresent(scope -> {
                    // Look for calls like JAXBToStringStrategy.getInstance() 
                    // and replace with JAXBToStringStrategy.INSTANCE
                    if (scope instanceof NameExpr) {
                        String className = ((NameExpr) scope).getNameAsString();
                        if (className.equals("JAXBToStringStrategy") ||
                            className.equals("JAXBEqualsStrategy") ||
                            className.equals("JAXBHashCodeStrategy") ||
                            className.equals("JAXBCopyStrategy") ||
                            className.equals("JAXBMergeStrategy")) {
                            
                            // Replace the call with the INSTANCE field
                            // Create expression to access the INSTANCE field instead
                            NameExpr instanceField = new NameExpr(className + ".INSTANCE");
                            methodCall.replace(instanceField);
                        }
                    }
                });
            }
        }
    }
}
