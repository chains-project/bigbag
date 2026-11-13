package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Parse all Java files in the source directory
        SourceRoot sourceRoot = new SourceRoot(sourcePath);
        sourceRoot.parse("", (localPath, absolutePath, result) -> {
            if (result.isSuccessful()) {
                CompilationUnit cu = result.getResult().orElseThrow();
                cu.accept(new GHCompareStatusFixer(), null);
            }
        });

        // Save changes
        sourceRoot.saveAll();
    }

    /**
     * Visitor that fixes GHCompare.status field access by replacing it with getStatus() method call.
     * This addresses a breaking change in github-api 1.313 where the status field became private.
     */
    private static class GHCompareStatusFixer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is accessing the status field
            if (n.getNameAsString().equals("status")) {
                // Check if the scope is a method call expression 
                if (n.getScope() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) n.getScope();
                    
                    // Check if this is a call to getCompare method
                    if (methodCall.getNameAsString().equals("getCompare")) {
                        // Replace the entire field access with a method call to getStatus()
                        // For example: getCompare(...).status becomes getCompare(...).getStatus()
                        return new MethodCallExpr(methodCall, "getStatus");
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}