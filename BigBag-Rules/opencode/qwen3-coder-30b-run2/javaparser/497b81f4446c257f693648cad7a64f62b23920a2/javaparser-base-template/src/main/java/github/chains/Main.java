package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to replace cactoos BytesOf and HexOf calls
 * with standard Java equivalents.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        if (!Files.exists(dir)) {
            System.err.println("Directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory recursively
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to replace Cactoos API calls
            CactoosReplacementVisitor visitor = new CactoosReplacementVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            // Note: Actual writing logic would go here, but for now we just process
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that identifies and replaces Cactoos BytesOf and HexOf usage.
     */
    private static class CactoosReplacementVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Handle cases like: new HexOf(new BytesOf(...))
            if ("HexOf".equals(n.getType().getNameAsString())) {
                if (n.getArguments().size() == 1) {
                    Expression argExpr = n.getArguments().get(0);
                    if (argExpr instanceof ObjectCreationExpr) {
                        ObjectCreationExpr innerCreation = (ObjectCreationExpr) argExpr;
                        if ("BytesOf".equals(innerCreation.getType().getNameAsString())) {
                            // Convert new HexOf(new BytesOf(...)) to HexFormat.of().formatHex(...)
                            return convertBytesOfToHexFormat(innerCreation);
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        private Expression convertBytesOfToHexFormat(ObjectCreationExpr bytesOfCreation) {
            // Extract the argument passed to BytesOf
            Expression bytesArgument = bytesOfCreation.getArguments().get(0);
            
            // Create new expression: HexFormat.of().formatHex(bytesArgument)
            // This assumes Java 17 or later with HexFormat available
            
            // Create the method call: HexFormat.of()
            MethodCallExpr hexFormatCall = new MethodCallExpr(
                new NameExpr("HexFormat"), 
                "of"
            );
            
            // Create the method call: .formatHex(bytesArgument)
            MethodCallExpr formatHexCall = new MethodCallExpr(
                hexFormatCall,
                "formatHex",
                new NodeList<>(bytesArgument)
            );
            
            return formatHexCall;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Handle asString() calls that might be chained
            if ("asString".equals(n.getNameAsString())) {
                // Check if this is part of the pattern: new HexOf(new BytesOf(...)).asString()
                if (n.getParentNode().isPresent() && 
                    n.getParentNode().get() instanceof MethodCallExpr) {
                    MethodCallExpr parent = (MethodCallExpr) n.getParentNode().get();
                    if (parent.getNameAsString().equals("new") && 
                        parent.getTypeArguments().isPresent() &&
                        "HexOf".equals(parent.getTypeArguments().get().get(0).toString())) {
                        // Replace the whole expression (new HexOf(...)).asString() 
                        // with the HexFormat equivalent
                        Expression replacement = convertHexOfAsStringToHexFormat(parent);
                        return replacement;
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        private Expression convertHexOfAsStringToHexFormat(MethodCallExpr hexOfCall) {
            // The expected pattern is: new HexOf(new BytesOf(digest))
            // We want to replace it with: HexFormat.of().formatHex(digest)
            
            if (hexOfCall.getArguments().size() != 1) {
                return hexOfCall; // Cannot process
            }
            
            Expression innerArg = hexOfCall.getArguments().get(0);
            if (!(innerArg instanceof ObjectCreationExpr)) {
                return hexOfCall; // Cannot process
            }
            
            ObjectCreationExpr bytesOfCreation = (ObjectCreationExpr) innerArg;
            if (!"BytesOf".equals(bytesOfCreation.getType().getNameAsString())) {
                return hexOfCall; // Cannot process
            }
            
            // Extract the digest argument to BytesOf
            if (bytesOfCreation.getArguments().size() != 1) {
                return hexOfCall; // Cannot process
            }
            
            Expression digestExpr = bytesOfCreation.getArguments().get(0);
            
            // Create new expression: HexFormat.of().formatHex(digest)
            MethodCallExpr hexFormatCall = new MethodCallExpr(
                new NameExpr("HexFormat"), 
                "of"
            );
            
            MethodCallExpr formatHexCall = new MethodCallExpr(
                hexFormatCall,
                "formatHex",
                new NodeList<>(digestExpr)
            );
            
            return formatHexCall;
        }
    }
}