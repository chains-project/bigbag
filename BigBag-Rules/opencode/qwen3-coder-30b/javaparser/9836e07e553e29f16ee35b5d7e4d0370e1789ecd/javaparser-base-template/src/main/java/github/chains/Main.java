package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation for fixing FailedCompletionStage constructor changes in asto-core.
 * Replaces new FailedCompletionStage<>(Throwable) with CompletableFuture.failedFuture(throwable)
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
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
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            FailedCompletionStageFixer fixer = new FailedCompletionStageFixer();
            fixer.visit(cu, null);
            cu.save();
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix FailedCompletionStage constructor usage.
     * Replaces new FailedCompletionStage<>(throwable) with CompletableFuture.failedFuture(throwable)
     */
    private static class FailedCompletionStageFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ConstructorCallExpr constructor, Void arg) {
            super.visit(constructor, arg);
            
            // Check if this is a FailedCompletionStage constructor call with a single Throwable argument
            if (constructor.getType().asString().equals("FailedCompletionStage") && 
                constructor.getArguments().size() == 1) {
                
                // Get the argument (should be a Throwable)
                Expression argExpr = constructor.getArguments().get(0);
                
                // Create replacement: CompletableFuture.failedFuture(argExpr)
                NameExpr completableFutureName = new NameExpr("CompletableFuture");
                MethodCallExpr failedFutureCall = new MethodCallExpr(
                    completableFutureName,
                    "failedFuture",
                    NodeList.nodeList(argExpr)
                );
                
                // Replace the constructor call with the new pattern
                constructor.replace(failedFutureCall);
                
                System.out.println("Fixed FailedCompletionStage constructor usage at: " + 
                    constructor.getBegin().get().line + ":" + 
                    constructor.getBegin().get().column);
            }
        }
    }
}