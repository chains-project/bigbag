package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation rule for fixing breaking API changes
 * in Maven projects that use Jenkins test harness or similar frameworks.
 * 
 * This transformation handles method signature changes by mapping old API calls
 * to new API calls with updated parameters.
 * 
 * Usage: java Main <source-directory>
 */
public class Main {
    
    /**
     * Main method to process Java files in a directory and apply transformations.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Main <source-directory>");
            System.out.println("Example: java Main /path/to/project/src");
            return;
        }
        
        String sourceDir = args[0];
        System.out.println("Processing files in: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process all Java files in a directory recursively.
     */
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    /**
     * Process a single Java file to find and transform API calls.
     */
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformations
            new ApiFixVisitor().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor class to find and fix API calls based on common breaking changes.
     */
    private static class ApiFixVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            String methodName = methodCall.getNameAsString();
            NodeList<Expression> arguments = methodCall.getArguments();
            
            // Pattern: Adding boolean parameter to createPipelineJobWithScript
            if ("createPipelineJobWithScript".equals(methodName) && arguments.size() == 1) {
                // Transform from: createPipelineJobWithScript(String script)
                // To: createPipelineJobWithScript(String script, boolean useWorkspace)
                arguments.add(new BooleanLiteralExpr(true));
                System.out.println("Fixed call to " + methodName);
            }
            
            // Pattern: Adding boolean parameter to createPipelineJobInFolderWithScript
            if ("createPipelineJobInFolderWithScript".equals(methodName) && arguments.size() == 2) {
                // Transform from: createPipelineJobInFolderWithScript(Folder folder, String script)
                // To: createPipelineJobInFolderWithScript(Folder folder, String script, boolean useWorkspace)
                arguments.add(new BooleanLiteralExpr(true));
                System.out.println("Fixed call to " + methodName);
            }
            
            // Pattern: Adding null parameter for configuration to createPipelineJobWithScript
            if ("createPipelineJobWithScript".equals(methodName) && arguments.size() == 1) {
                // Transform from: createPipelineJobWithScript(String script)
                // To: createPipelineJobWithScript(String script, String configuration)
                arguments.add(0, new NullLiteralExpr());
                System.out.println("Fixed call to " + methodName + " with configuration parameter");
            }
        }
    }
}