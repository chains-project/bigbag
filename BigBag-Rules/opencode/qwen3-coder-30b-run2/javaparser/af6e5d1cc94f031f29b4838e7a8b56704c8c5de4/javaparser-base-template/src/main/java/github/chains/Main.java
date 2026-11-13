package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path dir = Paths.get(sourceDirectory);
        
        if (!Files.exists(dir)) {
            System.err.println("Directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        try (Stream<Path> paths = Files.walk(dir).filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"))) {
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                transformFile(file);
            }
        }
    }
    
    private static void transformFile(Path filePath) throws IOException {
        String content = new String(Files.readAllBytes(filePath));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        FlywayTransformer transformer = new FlywayTransformer();
        transformer.visit(cu, null);
        
        if (transformer.isModified()) {
            // Write back the modified file
            try {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            } catch (IOException e) {
                System.err.println("Failed to write file " + filePath + ": " + e.getMessage());
            }
        }
    }
    
    private static class FlywayTransformer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if it's a call to Flyway constructor with no arguments
            if (n.getType().asString().equals("Flyway") && n.getArguments().isEmpty()) {
                // Replace with new Flyway(Flyway.configure())
                NodeList<com.github.javaparser.ast.expr.Expression> newArgs = new NodeList<>();
                newArgs.add(new MethodCallExpr(
                    new NameExpr("Flyway"),
                    "configure"
                ));
                
                n.setArguments(newArgs);
                modified = true;
            }
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}