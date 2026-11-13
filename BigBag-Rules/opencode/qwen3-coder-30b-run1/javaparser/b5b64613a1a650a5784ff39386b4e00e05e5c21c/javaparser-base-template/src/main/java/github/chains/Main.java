package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source-directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (IOException e) {
            e.printStackTrace();
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
            
            // Find and report ClientHelper constructor calls
            findClientHelperCalls(cu);
            
            // Find and report StringUtils.isAllBlank calls
            findIsAllBlankCalls(cu);
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static void findClientHelperCalls(CompilationUnit cu) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check for ClientHelper constructor calls
                if (n.getScope().isPresent() && 
                    n.getScope().get() instanceof NameExpr &&
                    n.getScope().get().toString().contains("ClientHelper")) {
                    System.out.println("Found ClientHelper constructor call: " + n.toString());
                }
            }
        }, null);
    }
    
    private static void findIsAllBlankCalls(CompilationUnit cu) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check for StringUtils.isAllBlank calls
                if (n.getScope().isPresent() && 
                    n.getScope().get() instanceof NameExpr &&
                    n.getScope().get().toString().contains("StringUtils")) {
                    
                    if ("isAllBlank".equals(n.getNameAsString())) {
                        System.out.println("Found isAllBlank call: " + n.toString());
                    }
                }
            }
        }, null);
    }
}