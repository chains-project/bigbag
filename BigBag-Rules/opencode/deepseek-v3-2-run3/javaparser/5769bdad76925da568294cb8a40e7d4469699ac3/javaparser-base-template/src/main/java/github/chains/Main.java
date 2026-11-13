package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <targetType> <fieldName> <methodName>");
            System.err.println("Example: java github.chains.Main /path/to/src org.kohsuke.github.GHCompare status getStatus");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String targetType = args[1];
        String fieldName = args[2];
        String methodName = args[3];
        
        System.out.println("Transforming field accesses to method calls:");
        System.out.println("  Source directory: " + sourceDir);
        System.out.println("  Target type: " + targetType);
        System.out.println("  Field name: " + fieldName);
        System.out.println("  Method name: " + methodName);
        
        List<File> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int transformedCount = 0;
        
        for (File javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile)) {
                CompilationUnit cu = parser.parse(in).getResult().get();
                
                FieldAccessTransformer transformer = new FieldAccessTransformer(targetType, fieldName, methodName);
                transformer.visit(cu, null);
                
                if (transformer.wasTransformed()) {
                    try (FileOutputStream out = new FileOutputStream(javaFile)) {
                        out.write(cu.toString().getBytes());
                    }
                    transformedCount++;
                    System.out.println("Transformed: " + javaFile.getPath());
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformation complete. Modified " + transformedCount + " files.");
    }
    
    private static class FieldAccessTransformer extends VoidVisitorAdapter<Void> {
        private final String targetType;
        private final String fieldName;
        private final String methodName;
        private boolean transformed = false;
        
        public FieldAccessTransformer(String targetType, String fieldName, String methodName) {
            this.targetType = targetType;
            this.fieldName = fieldName;
            this.methodName = methodName;
        }
        
        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this field access matches our target pattern
            if (n.getName().asString().equals(fieldName)) {
                // Replace field access with method call
                MethodCallExpr methodCall = new MethodCallExpr(n.getScope(), methodName);
                n.replace(methodCall);
                transformed = true;
            }
        }
        
        public boolean wasTransformed() {
            return transformed;
        }
    }
    
    private static List<File> findJavaFiles(String sourceDir) throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        }
    }
}