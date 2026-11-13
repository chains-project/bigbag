package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class SimpleFlywayFixer {
    
    // Mapping from old setter methods to new fluent builder methods
    private static final Map<String, String> METHOD_MAPPING = Map.of(
        "setDataSource", "dataSource",
        "setLocations", "locations",
        "setValidateOnMigrate", "validateOnMigrate"
    );
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(SimpleFlywayFixer::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser(new ParserConfiguration());
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
            SimpleFlywayTransformer transformer = new SimpleFlywayTransformer();
            CompilationUnit transformed = (CompilationUnit) transformer.visit(cu, null);
            
            if (transformer.wasModified()) {
                Files.write(filePath, transformed.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    private static class SimpleFlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this method call needs to be renamed
            String methodName = n.getNameAsString();
            String newMethodName = METHOD_MAPPING.get(methodName);
            
            if (newMethodName != null && !methodName.equals(newMethodName)) {
                // Check if this is part of a Flyway configuration chain
                if (isInFlywayChain(n)) {
                    n.setName(newMethodName);
                    modified = true;
                    System.out.println("Fixed method: " + methodName + " -> " + newMethodName);
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isInFlywayChain(MethodCallExpr call) {
            // Check if this call is part of a chain that starts with Flyway.configure()
            MethodCallExpr current = call;
            
            while (current.getScope().isPresent()) {
                Expression scope = current.getScope().get();
                if (scope instanceof MethodCallExpr) {
                    current = (MethodCallExpr) scope;
                    if (current.getNameAsString().equals("configure") && 
                        current.getScope().isPresent() && 
                        current.getScope().get().toString().equals("Flyway")) {
                        return true;
                    }
                } else {
                    break;
                }
            }
            
            return false;
        }
    }
}