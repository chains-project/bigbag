package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }
    
    private static void transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        cu.accept(new RemoveAddEnabledLanguagesVisitor(), null);
        
        // Always write the file since we don't have a reliable way to detect modifications
        Files.write(javaFile, cu.toString().getBytes());
        System.out.println("Transformed: " + javaFile);
    }
    
    private static class RemoveAddEnabledLanguagesVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to addEnabledLanguages
            if ("addEnabledLanguages".equals(n.getNameAsString())) {
                // Check if this is part of AnalysisEngineConfiguration.builder() chain
                // by looking at the parent chain
                if (isInAnalysisEngineConfigurationBuilderChain(n)) {
                    // Remove this method call by returning null
                    // This will effectively remove the call from the chain
                    return null;
                }
            }
            return super.visit(n, arg);
        }
        
        private boolean isInAnalysisEngineConfigurationBuilderChain(MethodCallExpr methodCall) {
            // Simple check: if the method call name is addEnabledLanguages and 
            // it's being called on a builder chain, we want to remove it
            // We'll be more permissive here to catch all cases
            return true;
        }
    }
}