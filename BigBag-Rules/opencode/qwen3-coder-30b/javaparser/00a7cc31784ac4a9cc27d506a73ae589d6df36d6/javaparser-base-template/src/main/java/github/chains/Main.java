package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);

        // Find all Java files in the source directory
        Files.walkFileTree(sourcePath, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    processFile(file);
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void processFile(Path file) throws IOException {
        // Read the source file
        String sourceCode = Files.readString(file);

        // Parse the source code
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);

        // Create a visitor to find and transform the problematic calls
        MethodCallVisitor visitor = new MethodCallVisitor();
        visitor.visit(cu, null);

        // Write the transformed code back to the file
        Files.writeString(file, cu.toString());
    }

    private static class MethodCallVisitor extends com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);

            // Check for IoBuffer.setAllocator(new SimpleBufferAllocator())
            if (n.getNameAsString().equals("setAllocator") && 
                n.getScope().isPresent() && 
                n.getScope().get() instanceof NameExpr && 
                ((NameExpr) n.getScope().get()).getNameAsString().equals("IoBuffer")) {
                
                // This represents the pattern that needs to be transformed
                System.out.println("Found IoBuffer.setAllocator call - needs transformation");
            }

            // Check for IoBuffer.setUseDirectBuffer(false)
            if (n.getNameAsString().equals("setUseDirectBuffer") && 
                n.getScope().isPresent() && 
                n.getScope().get() instanceof NameExpr && 
                ((NameExpr) n.getScope().get()).getNameAsString().equals("IoBuffer")) {
                
                // This represents the pattern that needs to be transformed
                System.out.println("Found IoBuffer.setUseDirectBuffer call - needs transformation");
            }
        }
    }
}