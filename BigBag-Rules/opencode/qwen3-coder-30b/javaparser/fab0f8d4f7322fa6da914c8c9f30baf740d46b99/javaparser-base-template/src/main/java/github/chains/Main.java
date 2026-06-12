package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation to replace org.cactoos.io.BytesOf and org.cactoos.text.HexOf usage
 * with standard Java alternatives.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Find all Java files in the source directory
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        for (Path file : javaFiles) {
            processFile(file);
        }
        
        System.out.println("Transformation completed for " + javaFiles.size() + " files");
    }
    
    private static void processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Create visitor to find and replace the pattern
        HexOfBytesOfVisitor visitor = new HexOfBytesOfVisitor();
        visitor.visit(cu, null);
        
        // Write back the modified content
        Files.writeString(filePath, cu.toString());
    }
    
    /**
     * Visitor that finds and replaces new HexOf(new BytesOf(...)) patterns.
     */
    private static class HexOfBytesOfVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ConstructorCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this constructor call is creating a HexOf with BytesOf argument
            if (n.getArguments().size() == 1) {
                Expression argExpr = n.getArguments().get(0);
                
                // Check if the argument is a BytesOf constructor call
                if (argExpr instanceof ConstructorCallExpr) {
                    ConstructorCallExpr bytesOfCall = (ConstructorCallExpr) argExpr;
                    if (isBytesOfCall(bytesOfCall)) {
                        // Replace with standard Java hex conversion
                        replaceHexOfBytesOf(n, bytesOfCall);
                    }
                }
            }
        }
        
        private boolean isBytesOfCall(ConstructorCallExpr expr) {
            // Check if it's a call to org.cactoos.io.BytesOf constructor
            String typeName = expr.getType().asString();
            return typeName.equals("org.cactoos.io.BytesOf") || 
                   typeName.equals("BytesOf");
        }
        
        private void replaceHexOfBytesOf(ConstructorCallExpr hexOfCall, ConstructorCallExpr bytesOfCall) {
            // Get the argument to BytesOf
            Expression bytesOfArg = bytesOfCall.getArguments().get(0);
            
            // Create a simple replacement for HexOf(new BytesOf(...))
            // This creates a call to java.util.HexFormat.of().formatHex(...)
            NameExpr hexFormatName = new NameExpr("java.util.HexFormat.of()");
            MethodCallExpr methodCall = new MethodCallExpr(hexFormatName, "formatHex", List.of(bytesOfArg));
            
            // Replace the HexOf constructor call with the hex format call
            hexOfCall.replace(methodCall);
        }
    }
}