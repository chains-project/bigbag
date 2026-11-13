package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic transformation to fix breaking changes in plexus-archiver 4.4.0
 * Specifically removes calls to enableLogging() on AbstractUnArchiver instances.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Find and remove enableLogging calls
            boolean modified = removeEnableLoggingCalls(cu);
            
            // If changes were made, save the file
            if (modified) {
                try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                    fos.write(cu.toString().getBytes());
                }
                System.out.println("Processed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean removeEnableLoggingCalls(CompilationUnit cu) {
        // First collect all statements to remove
        List<Statement> statementsToRemove = new ArrayList<>();
        EnableLoggingRemover visitor = new EnableLoggingRemover(statementsToRemove);
        visitor.visit(cu, null);
        
        // Then remove them
        boolean modified = !statementsToRemove.isEmpty();
        if (modified) {
            for (Statement stmt : statementsToRemove) {
                Optional<BlockStmt> parentBlock = stmt.getParentNode()
                    .filter(node -> node instanceof BlockStmt)
                    .map(node -> (BlockStmt) node);
                
                if (parentBlock.isPresent()) {
                    parentBlock.get().remove(stmt);
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Visitor to find enableLogging calls on AbstractUnArchiver instances
     */
    private static class EnableLoggingRemover extends VoidVisitorAdapter<List<Statement>> {
        private final List<Statement> statementsToRemove;
        
        public EnableLoggingRemover(List<Statement> statementsToRemove) {
            this.statementsToRemove = statementsToRemove;
        }
        
        @Override
        public void visit(MethodCallExpr methodCall, List<Statement> arg) {
            super.visit(methodCall, arg);
            
            // Match method calls to enableLogging
            if (methodCall.getNameAsString().equals("enableLogging")) {
                // Find the statement that contains this method call
                Optional<Statement> parentStatement = methodCall.getParentNode()
                    .filter(node -> node instanceof Statement)
                    .map(node -> (Statement) node);
                
                if (parentStatement.isPresent()) {
                    statementsToRemove.add(parentStatement.get());
                }
            }
        }
    }
}
