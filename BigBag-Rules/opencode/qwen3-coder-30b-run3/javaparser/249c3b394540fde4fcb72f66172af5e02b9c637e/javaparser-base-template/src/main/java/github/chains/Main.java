package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.PrettyPrinter;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations
            cu.accept(new DumperOptionsTransformationVisitor(), null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(new PrettyPrinter().print(cu));
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that transforms DumperOptions constructor calls and method invocations
     * to handle SnakeYAML API breaking changes
     */
    private static class DumperOptionsTransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Look for DumperOptions constructor calls
            if (n.getType().getNameAsString().equals("DumperOptions")) {
                // This is where we'd handle constructor signature changes
                // Placeholder for future enhancement
            }
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Transform DumperOptions method calls that may have changed signatures
            // Match calls that set properties like indent, width, etc.
            if (n.getScope().isPresent() && n.getScope().get() instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) n.getScope().get();
                if (nameExpr.getNameAsString().contains("dumperOptions") || 
                    nameExpr.getNameAsString().contains("options") ||
                    nameExpr.getNameAsString().contains("DumperOptions")) {
                    
                    // Handle specific method calls that had breaking changes in SnakeYAML
                    String methodName = n.getNameAsString();
                    
                    // Handle the most common breaking change: setPrettyFlow(boolean) -> setPrettyFlow(Boolean)
                    if (methodName.equals("setPrettyFlow")) {
                        // This would transform calls to handle enum vs boolean changes
                        // The transformation logic would be implemented here
                    }
                    
                    // Handle setIndent(int) -> setIndent(int, boolean) or similar signature changes
                    if (methodName.equals("setIndent") && n.getArguments().size() > 0) {
                        // Handle potential signature changes for setIndent
                    }
                }
            }
        }
    }
}
