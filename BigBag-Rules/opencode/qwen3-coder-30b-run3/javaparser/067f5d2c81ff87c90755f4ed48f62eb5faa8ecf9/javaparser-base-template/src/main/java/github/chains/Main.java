package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic transformation rule to identify TSerializer.serialize() calls that need exception handling
 * due to breaking change in libthrift 0.16.0 where serialize() now throws TException instead of TTransportException
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(sourceDirectory);
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        Files.walk(Paths.get(directoryPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(java.nio.file.Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the visitor to find TSerializer.serialize() calls
            new TSerializerFinderVisitor().visit(cu, null);
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor to find TSerializer.serialize() calls
     */
    private static class TSerializerFinderVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a TSerializer.serialize() call
            String methodName = methodCall.getNameAsString();
            if ("serialize".equals(methodName)) {
                // Check if it's a call to TSerializer.serialize() by checking scope
                if (methodCall.getScope().map(scope -> 
                    scope.toString().contains("TSerializer") || 
                    scope.toString().contains("serializer") ||
                    scope.toString().contains("new TSerializer()")
                ).orElse(false)) {
                    // Report the location of the call
                    System.out.println("TSerializer.serialize() call found at: " + 
                        filePath + " line " + 
                        methodCall.getRange().map(r -> "" + r.begin.line).orElse("unknown"));
                }
            }
        }
    }
}