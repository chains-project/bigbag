package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(new File(sourceDirectory));
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            throw new IOException("Directory does not exist: " + directory);
        }
        
        List<File> javaFiles = Files.walk(directory.toPath())
            .filter(path -> path.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
            
        for (File file : javaFiles) {
            processFile(file);
        }
    }
    
    private static void processFile(File file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean modified = false;
            
            // Find all method calls to MockitoAnnotations.initMocks and replace with openMocks
            List<MethodCallExpr> initMocksCalls = cu.findAll(MethodCallExpr.class).stream()
                .filter(Main::isInitMocksCall)
                .collect(Collectors.toList());
                
            for (MethodCallExpr call : initMocksCalls) {
                // Replace with MockitoAnnotations.openMocks
                call.setName("openMocks");
                modified = true;
            }
            
            // Find all method calls to MockitoAnnotations.initMocks with explicit scope
            List<MethodCallExpr> scopedInitMocksCalls = cu.findAll(MethodCallExpr.class).stream()
                .filter(Main::isScopedInitMocksCall)
                .collect(Collectors.toList());
                
            for (MethodCallExpr call : scopedInitMocksCalls) {
                // Replace with MockitoAnnotations.openMocks
                call.setName("openMocks");
                modified = true;
            }
            
            if (modified) {
                System.out.println("Modified: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
    
    private static boolean isInitMocksCall(MethodCallExpr call) {
        // Check if it's a call to MockitoAnnotations.initMocks
        if (call.getNameAsString().equals("initMocks")) {
            // Check if the scope is MockitoAnnotations
            if (call.getScope().isPresent()) {
                String scopeStr = call.getScope().get().toString();
                if (scopeStr.equals("MockitoAnnotations")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static boolean isScopedInitMocksCall(MethodCallExpr call) {
        // Check if it's a call to MockitoAnnotations.initMocks with a qualified name
        if (call.getNameAsString().equals("initMocks")) {
            if (call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                if (scope instanceof FieldAccessExpr) {
                    FieldAccessExpr fieldAccess = (FieldAccessExpr) scope;
                    if (fieldAccess.getNameAsString().equals("MockitoAnnotations")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}