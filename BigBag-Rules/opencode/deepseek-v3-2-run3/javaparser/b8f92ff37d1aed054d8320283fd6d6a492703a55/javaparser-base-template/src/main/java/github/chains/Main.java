package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule for the breaking change in maven-surefire-common 3.0.0-M7.
 * 
 * Breaking Change: TestListResolver.getWildcard() method was removed
 * Replacement: TestListResolver.getEmptyTestListResolver()
 * 
 * This transformation automatically fixes compilation errors caused by this
 * API change in any Java project using maven-surefire-common 3.0.0-M7 or later.
 * 
 * Usage: java Main <source-directory>
 */
public class Main {
    
    // Configuration: Old and new method signatures
    private static final String OLD_CLASS_NAME = "TestListResolver";
    private static final String OLD_METHOD_NAME = "getWildcard";
    private static final String NEW_METHOD_NAME = "getEmptyTestListResolver";
    private static final String FULLY_QUALIFIED_OLD_CLASS = "org.apache.maven.surefire.api.testset.TestListResolver";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("\nTransformation: " + OLD_CLASS_NAME + "." + OLD_METHOD_NAME + "() -> " + 
                             OLD_CLASS_NAME + "." + NEW_METHOD_NAME + "()");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: " + OLD_CLASS_NAME + "." + OLD_METHOD_NAME + "() -> " + 
                          OLD_CLASS_NAME + "." + NEW_METHOD_NAME + "()");
        
        try {
            List<Path> javaFiles = findAllJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            int totalChanges = 0;
            
            for (Path javaFile : javaFiles) {
                int changes = processFile(javaFile);
                if (changes > 0) {
                    modifiedFiles++;
                    totalChanges += changes;
                    System.out.println("Modified " + javaFile + " (" + changes + " changes)");
                }
            }
            
            System.out.println("\nSummary: Modified " + modifiedFiles + " files with " + totalChanges + " total changes");
            System.out.println("\nBreaking Change Fixed: " + OLD_CLASS_NAME + "." + OLD_METHOD_NAME + "() was removed in maven-surefire-common 3.0.0-M7");
            System.out.println("Replacement API: " + OLD_CLASS_NAME + "." + NEW_METHOD_NAME + "()");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static List<Path> findAllJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String filename = p.toString();
                    return filename.endsWith(".java") && 
                           !filename.contains("target") && 
                           !filename.contains(".git") &&
                           !filename.contains(".class");
                })
                .collect(Collectors.toList());
        }
    }
    
    private static int processFile(Path javaFile) {
        try {
            byte[] fileBytes = Files.readAllBytes(javaFile);
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(content).getResult().orElseThrow();
            
            boolean modified = false;
            int changeCount = 0;
            
            // Find all method calls matching the old API pattern
            List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class, expr -> 
                isTargetMethodCall(expr)
            );
            
            for (MethodCallExpr methodCall : methodCalls) {
                // Replace with new API
                MethodCallExpr replacement = new MethodCallExpr(
                    methodCall.getScope().orElse(new NameExpr(OLD_CLASS_NAME)),
                    NEW_METHOD_NAME
                );
                
                methodCall.replace(replacement);
                modified = true;
                changeCount++;
            }
            
            if (modified) {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String newContent = printer.print(cu);
                Files.writeString(javaFile, newContent, StandardCharsets.UTF_8);
            }
            
            return changeCount;
        } catch (Exception e) {
            System.err.println("Warning: Could not process file " + javaFile + ": " + e.getMessage());
            return 0;
        }
    }
    
    private static boolean isTargetMethodCall(MethodCallExpr expr) {
        if (!expr.getNameAsString().equals(OLD_METHOD_NAME)) {
            return false;
        }
        
        if (expr.getScope().isPresent()) {
            String scope = expr.getScope().get().toString();
            // Match simple name, qualified name, or fully qualified name
            return scope.equals(OLD_CLASS_NAME) || 
                   scope.endsWith("." + OLD_CLASS_NAME) ||
                   scope.equals(FULLY_QUALIFIED_OLD_CLASS);
        }
        return false;
    }
}