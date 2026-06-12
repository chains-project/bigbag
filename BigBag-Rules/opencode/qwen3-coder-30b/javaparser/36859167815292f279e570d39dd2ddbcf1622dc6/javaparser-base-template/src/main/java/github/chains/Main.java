package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
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
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to fix FopFactory.newInstance() calls
            new FopFactoryTransformationVisitor().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class FopFactoryTransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Match FopFactory.newInstance() calls without arguments
            // Pattern: FopFactory.newInstance() -> FopFactory.newInstance(new File(".").toURI())
            if (methodCall.getNameAsString().equals("newInstance") && 
                methodCall.getArguments().isEmpty() &&
                methodCall.getScope().isPresent()) {
                
                String scopeStr = methodCall.getScope().get().toString();
                if (scopeStr.equals("FopFactory")) {
                    // Replace with FopFactory.newInstance(new File(".").toURI())
                    // This is a common fallback pattern that works in FOP 2.2
                    methodCall.addArgument("new java.io.File(\".\").toURI()");
                }
            }
        }
    }
}
