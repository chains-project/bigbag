package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleFix {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar simplefix.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser javaParser = new JavaParser();
        int filesChanged = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                boolean modified = false;
                
                // Fix query1NN to query1nn
                List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
                for (MethodCallExpr methodCall : methodCalls) {
                    if (methodCall.getNameAsString().equals("query1NN")) {
                        methodCall.setName("query1nn");
                        System.out.println("Fixed query1NN -> query1nn in " + javaFile);
                        modified = true;
                    }
                }
                
                // Write the modified file back if changes were made
                if (modified) {
                    PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
                    config.setPrintComments(true);
                    config.setColumnAlignParameters(true);
                    config.setColumnAlignFirstMethodChain(false);
                    
                    String modifiedCode = cu.toString(config);
                    Files.write(javaFile, modifiedCode.getBytes());
                    filesChanged++;
                }
                
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete!");
        System.out.println("Files changed: " + filesChanged);
    }
}