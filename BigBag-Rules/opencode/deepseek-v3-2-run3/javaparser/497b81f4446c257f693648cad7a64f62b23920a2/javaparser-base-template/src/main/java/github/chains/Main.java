package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for API breaking changes where method parameters
 * changed from Iterable<Map.Entry<String, String>> to Headers.
 * 
 * <p>Transformation pattern:
 * Old API: methodName(..., Iterable<Map.Entry<String, String>> arg, ...)
 * New API: methodName(..., Headers arg, ...)
 * Transformation: methodName(..., new Headers.From(arg), ...)
 * 
 * <p>This is a generic rule applicable to any project where method signatures
 * changed from accepting Iterable to accepting a specific wrapper type.
 * The rule is parameterized by:
 * 1. Target type to wrap to (e.g., Headers)
 * 2. Wrapper constructor (e.g., Headers.From)
 * 3. Method patterns to transform
 */
public class Main {
    
    // Configurable transformation parameters
    // These could be made command-line arguments for full generality
    private static final String TARGET_TYPE_FQN = "com.artipie.http.Headers";
    private static final String WRAPPER_CONSTRUCTOR_FQN = "com.artipie.http.Headers.From";
    
    // Method patterns to transform: method name and parameter index to wrap
    // This could be loaded from a configuration file
    private static final class MethodPattern {
        final String methodName;
        final int paramIndex;
        
        MethodPattern(String methodName, int paramIndex) {
            this.methodName = methodName;
            this.paramIndex = paramIndex;
        }
    }
    
    // Common method patterns in com.artipie.http API that might need transformation
    // In a real-world scenario, these would be discovered by analyzing the API diff
    private static final List<MethodPattern> PATTERNS = List.of(
        new MethodPattern("accept", 1),    // Connection.accept(RsStatus, Iterable, Publisher)
        new MethodPattern("response", 1)    // Slice.response(String, Iterable, Publisher)
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms Iterable<Map.Entry<String,String>> to Headers in method calls");
            System.exit(1);
        }
        
        Path sourceDir = Path.of(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
        List<Path> javaFiles = new ArrayList<>();
        
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matcher.matches(file)) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }
    
    private static void transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + javaFile)
        );
        
        boolean modified = cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                MethodCallExpr transformed = transformMethodCall(n);
                if (transformed != n) {
                    return transformed;
                }
                return super.visit(n, arg);
            }
        }, null) != null;
        
        if (modified) {
            Files.writeString(javaFile, cu.toString());
            System.out.println("Modified: " + javaFile);
        }
    }
    
    private static MethodCallExpr transformMethodCall(MethodCallExpr n) {
        String methodName = n.getNameAsString();
        
        // Check against known patterns
        for (MethodPattern pattern : PATTERNS) {
            if (pattern.methodName.equals(methodName) && n.getArguments().size() > pattern.paramIndex) {
                return wrapIterableToHeaders(n, pattern.paramIndex);
            }
        }
        
        return n;
    }
    
    private static MethodCallExpr wrapIterableToHeaders(MethodCallExpr n, int argIndex) {
        // Create new WrapperType(iterable) where WrapperType is the target type
        ObjectCreationExpr wrapper = new ObjectCreationExpr();
        wrapper.setType(WRAPPER_CONSTRUCTOR_FQN);
        wrapper.addArgument(n.getArgument(argIndex));
        
        // Replace the argument
        n.getArguments().set(argIndex, wrapper);
        return n;
    }
}