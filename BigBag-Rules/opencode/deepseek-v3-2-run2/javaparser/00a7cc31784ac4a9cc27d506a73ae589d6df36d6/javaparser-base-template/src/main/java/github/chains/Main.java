package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for mina-core 2.2.1 breaking API changes.
 * Removes calls to deprecated IoBuffer static methods that were removed in mina-core 2.2.1:
 * - IoBuffer.setAllocator(IoBufferAllocator)
 * - IoBuffer.setUseDirectBuffer(boolean)
 */
public class Main {
    
    private static final String DEPENDENCY_PACKAGE = "org.apache.mina.core.buffer.IoBuffer";
    private static final String SET_ALLOCATOR_METHOD = "setAllocator";
    private static final String SET_USE_DIRECT_BUFFER_METHOD = "setUseDirectBuffer";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Applies transformation to fix mina-core 2.2.1 breaking changes");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int removedCalls = 0;
            
            for (Path javaFile : javaFiles) {
                TransformationResult result = transformFile(javaFile);
                if (result.wasTransformed()) {
                    transformedFiles++;
                    removedCalls += result.getRemovedCalls();
                    System.out.println("Transformed: " + javaFile + " (removed " + result.getRemovedCalls() + " calls)");
                }
            }
            
            System.out.println("\nTransformation completed:");
            System.out.println("- Files transformed: " + transformedFiles);
            System.out.println("- Method calls removed: " + removedCalls);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        return Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static TransformationResult transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        CompilationUnit cu = JavaParser.parse(content);
        
        IoBufferMethodRemover remover = new IoBufferMethodRemover();
        cu.accept(remover, null);
        
        if (remover.getRemovedCalls() > 0) {
            Files.writeString(javaFile, cu.toString());
            return new TransformationResult(true, remover.getRemovedCalls());
        }
        
        return new TransformationResult(false, 0);
    }
    
    /**
     * Visitor that removes calls to deprecated IoBuffer static methods
     */
    private static class IoBufferMethodRemover extends ModifierVisitor<Void> {
        private int removedCalls = 0;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a static method call on IoBuffer
            if (n.getScope().isPresent()) {
                String scope = n.getScope().get().toString();
                String methodName = n.getNameAsString();
                
                // Match IoBuffer.setAllocator() or IoBuffer.setUseDirectBuffer()
                if (scope.equals("IoBuffer") || scope.equals(DEPENDENCY_PACKAGE)) {
                    if (methodName.equals(SET_ALLOCATOR_METHOD) || methodName.equals(SET_USE_DIRECT_BUFFER_METHOD)) {
                        removedCalls++;
                        // Remove the method call by returning null
                        return null;
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        public int getRemovedCalls() {
            return removedCalls;
        }
    }
    
    /**
     * Result of file transformation
     */
    private static class TransformationResult {
        private final boolean transformed;
        private final int removedCalls;
        
        public TransformationResult(boolean transformed, int removedCalls) {
            this.transformed = transformed;
            this.removedCalls = removedCalls;
        }
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        public int getRemovedCalls() {
            return removedCalls;
        }
    }
}