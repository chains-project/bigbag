package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            new AddEnabledLanguagesFixVisitor().visit(cu, null);
            // Save the modified file
            cu.toString(); // This is just a placeholder for the actual save logic
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class AddEnabledLanguagesFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Match the old API pattern: .addEnabledLanguages(...)
            if (methodCall.getNameAsString().equals("addEnabledLanguages")) {
                // Check if this is a call on AnalysisEngineConfiguration.Builder
                if (methodCall.getScope().isPresent()) {
                    // Create replacement: setEnabledLanguages(...)
                    MethodCallExpr newCall = new MethodCallExpr(
                        methodCall.getScope().get(),
                        "setEnabledLanguages",
                        methodCall.getArguments()
                    );
                    methodCall.replace(newCall);
                }
            }
        }
    }
}