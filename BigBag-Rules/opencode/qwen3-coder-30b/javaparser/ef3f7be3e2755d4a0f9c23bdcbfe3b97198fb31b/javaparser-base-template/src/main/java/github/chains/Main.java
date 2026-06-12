package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        // Process all Java files in the PGS project
        String sourceDir = "/workspace/PGS/src/main/java";
        String targetDir = "/workspace/PGS/src/main/java";
        
        // Create a visitor to apply transformations
        TransformationVisitor visitor = new TransformationVisitor();
        
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
                
            for (Path file : javaFiles) {
                processFile(file, targetDir, visitor);
            }
        }
        
        System.out.println("Transformation completed on all Java files");
    }
    
    private static void processFile(Path sourceFile, String targetDir, TransformationVisitor visitor) throws IOException {
        // Read the file content
        String content = new String(Files.readAllBytes(sourceFile));
        
        // Parse the file
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply transformations
        cu.accept(visitor, null);
        
        // Write back to file
        Path targetFile = Paths.get(targetDir, sourceFile.toString().substring(sourceFile.toString().lastIndexOf("/")));
        Files.write(targetFile, cu.toString().getBytes());
    }
    
    private static class TransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Transform query1NN to query1nn
            if (n.getNameAsString().equals("query1NN")) {
                n.setName("query1nn");
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            super.visit(n, arg);
            
            // Transform PointIndex to PointMap
            if (n.getNameAsString().equals("PointIndex")) {
                n.setName("PointMap");
            }
            
            // Transform PointDistanceFunction to PointDistance
            if (n.getNameAsString().equals("PointDistanceFunction")) {
                n.setName("PointDistance");
            }
        }
    }
}