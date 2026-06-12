package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        for (Path javaFile : javaFiles) {
            boolean fileModified = false;
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(in).getResult().orElseThrow();
                
                DnsApiTransformer transformer = new DnsApiTransformer();
                transformer.visit(cu, null);
                
                if (transformer.getTransformationCount() > 0) {
                    try (FileOutputStream out = new FileOutputStream(javaFile.toFile())) {
                        out.write(cu.toString().getBytes());
                    }
                    transformedFiles++;
                    transformedCalls += transformer.getTransformationCount();
                    System.out.println("Transformed " + transformer.getTransformationCount() + 
                                     " calls in " + javaFile);
                    fileModified = true;
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Files modified: " + transformedFiles);
        System.out.println("  Method calls transformed: " + transformedCalls);
    }
    
    private static class DnsApiTransformer extends VoidVisitorAdapter<Void> {
        private int transformationCount = 0;
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Pattern 1: managedZones().create(project, managedZone)
            // Becomes: managedZones().create(project, "global", managedZone)
            if (isMethodCall(n, "create") && hasArgumentCount(n, 2)) {
                if (isCallChainContaining(n, "managedZones")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
            
            // Pattern 2: managedZones().get(project, managedZoneName)
            // Becomes: managedZones().get(project, "global", managedZoneName)
            if (isMethodCall(n, "get") && hasArgumentCount(n, 2)) {
                if (isCallChainContaining(n, "managedZones")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
            
            // Pattern 3: managedZones().list(project)
            // Becomes: managedZones().list(project, "global")
            if (isMethodCall(n, "list") && hasArgumentCount(n, 1)) {
                if (isCallChainContaining(n, "managedZones")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
            
            // Pattern 4: managedZones().delete(project, managedZoneName)
            // Becomes: managedZones().delete(project, "global", managedZoneName)
            if (isMethodCall(n, "delete") && hasArgumentCount(n, 2)) {
                if (isCallChainContaining(n, "managedZones")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
            
            // Pattern 5: resourceRecordSets().list(project, managedZoneName)
            // Becomes: resourceRecordSets().list(project, "global", managedZoneName)
            if (isMethodCall(n, "list") && hasArgumentCount(n, 2)) {
                if (isCallChainContaining(n, "resourceRecordSets")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
            
            // Pattern 6: projects().get(project)
            // Becomes: projects().get(project, "global")
            if (isMethodCall(n, "get") && hasArgumentCount(n, 1)) {
                if (isCallChainContaining(n, "projects")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
            
            // Pattern 7: changes().create(project, managedZoneName, change)
            // Becomes: changes().create(project, "global", managedZoneName, change)
            if (isMethodCall(n, "create") && hasArgumentCount(n, 3)) {
                if (isCallChainContaining(n, "changes")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
            
            // Pattern 8: changes().get(project, managedZoneName, changeId)
            // Becomes: changes().get(project, "global", managedZoneName, changeId)
            if (isMethodCall(n, "get") && hasArgumentCount(n, 3)) {
                if (isCallChainContaining(n, "changes")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
            
            // Pattern 9: changes().list(project, managedZoneName)
            // Becomes: changes().list(project, "global", managedZoneName)
            if (isMethodCall(n, "list") && hasArgumentCount(n, 2)) {
                if (isCallChainContaining(n, "changes")) {
                    insertLocationParameter(n, 1);
                    transformationCount++;
                }
            }
        }
        
        private boolean isMethodCall(MethodCallExpr n, String methodName) {
            return n.getNameAsString().equals(methodName);
        }
        
        private boolean hasArgumentCount(MethodCallExpr n, int expectedCount) {
            return n.getArguments().size() == expectedCount;
        }
        
        private boolean isCallChainContaining(MethodCallExpr n, String methodName) {
            // Check if this method call is part of a chain containing the specified method
            if (n.getScope().isPresent()) {
                var scope = n.getScope().get();
                if (scope instanceof MethodCallExpr) {
                    MethodCallExpr parentCall = (MethodCallExpr) scope;
                    if (parentCall.getNameAsString().equals(methodName)) {
                        return true;
                    }
                    return isCallChainContaining(parentCall, methodName);
                }
            }
            return false;
        }
        
        private void insertLocationParameter(MethodCallExpr n, int position) {
            // Insert "global" as the location parameter at the specified position
            n.getArguments().add(position, new StringLiteralExpr("global"));
        }
    }
}