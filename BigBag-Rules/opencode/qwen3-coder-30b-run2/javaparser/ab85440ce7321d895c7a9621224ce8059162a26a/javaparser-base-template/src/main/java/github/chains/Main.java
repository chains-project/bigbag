package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix Hamcrest StringContains and StringStartsWith constructor calls
 * that changed from (boolean, String) to (String) in newer versions.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        // Process all Java files in the directory
        SourceRoot root = new SourceRoot(sourcePath);
        root.parse("", (localPath, ast) -> {
            if (ast.getCompilationUnit().isPresent()) {
                CompilationUnit cu = ast.getCompilationUnit().get();
                new HamcrestFixVisitor().visit(cu, null);
            }
            return ast;
        });
        
        // Save modified files back to disk
        root.saveAll();
    }
    
    /**
     * Visitor to find and fix StringContains and StringStartsWith constructor calls
     */
    private static class HamcrestFixVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a StringContains or StringStartsWith constructor call
            if (n.getType().getNameAsString().equals("StringContains") ||
                n.getType().getNameAsString().equals("StringStartsWith")) {
                
                // Check if it's using the old constructor signature (boolean, String)
                NodeList<Expression> args = n.getArguments();
                if (args.size() == 2) {
                    // Check if first argument is a boolean literal
                    Expression firstArg = args.get(0);
                    if (firstArg instanceof BooleanLiteralExpr) {
                        // Extract the second argument (the string)
                        Expression secondArg = args.get(1);
                        if (secondArg instanceof StringLiteralExpr) {
                            // Replace the constructor with the new signature (only String)
                            n.setArguments(NodeList.nodeList(secondArg));
                        }
                    }
                }
            }
        }
    }
}