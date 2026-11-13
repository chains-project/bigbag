package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (Exception e) {
            e.printStackTrace();
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
            new MethodCallVisitor().visit(cu, null);
            
            // Write back to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class MethodCallVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is the specific pattern we want to fix:
            // getContainer().getLoggerManager().setThreshold(...)
            if (methodCall.getNameAsString().equals("setThreshold")) {
                // Check if it's a call to getLoggerManager
                if (methodCall.getScope().isPresent()) {
                    if (methodCall.getScope().get() instanceof MethodCallExpr) {
                        MethodCallExpr parentCall = (MethodCallExpr) methodCall.getScope().get();
                        if (parentCall.getNameAsString().equals("getLoggerManager")) {
                            if (parentCall.getScope().isPresent()) {
                                if (parentCall.getScope().get() instanceof MethodCallExpr) {
                                    MethodCallExpr grandParentCall = (MethodCallExpr) parentCall.getScope().get();
                                    if (grandParentCall.getNameAsString().equals("getContainer")) {
                                        // Replace with: getContainer().getLogger().setLevel(...)
                                        MethodCallExpr newMethodCall = new MethodCallExpr(
                                            new MethodCallExpr(grandParentCall, "getLogger"),
                                            "setLevel"
                                        );
                                        
                                        // Copy the arguments from the original call
                                        newMethodCall.setArguments(methodCall.getArguments());
                                        
                                        // Replace the old method call with the new one
                                        methodCall.replace(newMethodCall);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}