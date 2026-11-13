package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation to fix tinspin-indexes 2.0.1 breaking changes.
 * 
 * This transformation identifies problematic queryKnn calls that need to be manually fixed.
 * 
 * The breaking change: In tinspin-indexes 2.0.1, queryKnn methods now require a PointDistance parameter.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        // This transformation identifies breaking changes in tinspin-indexes 2.0.1
        // Specifically, queryKnn methods that need PointDistance parameter added
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dirPath = Paths.get(sourceDir);
        
        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Create visitor to find and identify breaking changes
            BreakingChangeFinder visitor = new BreakingChangeFinder();
            visitor.visit(cu, null);
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to identify potentially problematic queryKnn calls
     */
    private static class BreakingChangeFinder extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for queryKnn method calls
            if (methodCall.getNameAsString().equals("queryKnn")) {
                // Report potential breaking change
                if (methodCall.getScope().isPresent()) {
                    // If this looks like a tinspin call, report it
                    if (isLikelyTinspinCall(methodCall.getScope().get())) {
                        System.out.println("Potential breaking change detected:");
                        System.out.println("  Method call: " + methodCall.toString());
                        System.out.println("  Fix: Add PointDistance.L2 as third parameter");
                        System.out.println();
                    }
                }
            }
        }
        
        private boolean isLikelyTinspinCall(Object scope) {
            // Simple heuristics to identify tinspin index calls
            if (scope != null) {
                String scopeStr = scope.toString().toLowerCase();
                return scopeStr.contains("tinspin") || 
                       scopeStr.contains("index") ||
                       scopeStr.contains("tree");
            }
            return false;
        }
    }
}