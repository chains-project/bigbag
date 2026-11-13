package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Generic transformation rule to fix breaking changes in AnalysisEngineConfiguration.Builder.addEnabledLanguages
 * This transforms the method call pattern for sonarlint-core API breaking changes
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix the method call pattern
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr methodCall, Void arg) {
                    super.visit(methodCall, arg);
                    
                    // Match the old method name pattern: addEnabledLanguages(...)
                    if (methodCall.getNameAsString().equals("addEnabledLanguages")) {
                        // Transform to: setEnabledLanguages(...)
                        methodCall.setName("setEnabledLanguages");
                    }
                }
            }, null);
            
            // Save the modified file
            String code = cu.toString();
            Files.write(filePath, code.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Skip files that can't be parsed
            // This is expected for some malformed files or those with unsupported syntax
        }
    }
}
