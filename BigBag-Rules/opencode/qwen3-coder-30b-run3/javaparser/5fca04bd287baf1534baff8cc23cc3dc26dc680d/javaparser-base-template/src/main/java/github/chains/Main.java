package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ImportDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation to replace cactoos usage with standard Java equivalents.
 * This fixes the breaking change in http v1.1.3 where cactoos classes were removed.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        String sourceDir = "/workspace/docker-adapter";
        if (args.length > 0) {
            sourceDir = args[0];
        }
        
        Path dir = Paths.get(sourceDir);
        if (!Files.exists(dir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory recursively
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to replace cactoos usage
            CactoosReplacementVisitor visitor = new CactoosReplacementVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                LexicalPreservingPrinter.print(cu, writer);
            }
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to process " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that replaces cactoos usage with standard Java equivalents.
     */
    private static class CactoosReplacementVisitor extends ModifierVisitor<Void> {
        @Override
        public Expression visit(MethodCallExpr n, Void arg) {
            Expression expr = super.visit(n, arg);
            
            // Look for new HexOf(new BytesOf(...)).asString() patterns
            if (isHexOfBytesOfAsCall(n)) {
                return replaceWithByteArrayToString(n);
            }
            
            return expr;
        }
        
        private boolean isHexOfBytesOfAsCall(Expression expr) {
            // Check: new HexOf(new BytesOf(...)).asString()
            if (!(expr instanceof MethodCallExpr)) {
                return false;
            }
            
            MethodCallExpr methodCall = (MethodCallExpr) expr;
            
            if (!methodCall.getNameAsString().equals("asString")) {
                return false;
            }
            
            // Check if the scope is a HexOf constructor call
            if (!methodCall.getScope().isPresent()) {
                return false;
            }
            
            Expression scope = methodCall.getScope().get();
            if (!(scope instanceof MethodCallExpr)) {
                return false;
            }
            
            MethodCallExpr scopeCall = (MethodCallExpr) scope;
            if (!scopeCall.getNameAsString().equals("new")) {
                return false;
            }
            
            // Check if it's the HexOf constructor
            if (scopeCall.getExpression().isNameExpr()) {
                return scopeCall.getExpression().asNameExpr().getNameAsString().equals("HexOf");
            }
            
            return false;
        }
        
        private Expression replaceWithByteArrayToString(Expression n) {
            // Replace: new HexOf(new BytesOf(byteArray)).asString()
            // With: Arrays.toString(byteArray).replaceAll("[\\\\[\\\\], ]", "")
            
            MethodCallExpr methodCall = (MethodCallExpr) n;
            
            // Get the scope (which should be HexOf constructor)
            if (!methodCall.getScope().isPresent()) {
                return n;
            }
            
            Expression scope = methodCall.getScope().get();
            if (!(scope instanceof MethodCallExpr)) {
                return n;
            }
            
            MethodCallExpr scopeCall = (MethodCallExpr) scope;
            
            // Get the BytesOf constructor call
            if (scopeCall.getArguments().size() != 1) {
                return n;
            }
            
            Expression bytesOfArg = scopeCall.getArguments().get(0);
            if (!(bytesOfArg instanceof ObjectCreationExpr)) {
                return n;
            }
            
            ObjectCreationExpr bytesOf = (ObjectCreationExpr) bytesOfArg;
            if (!bytesOf.getType().getNameAsString().equals("BytesOf")) {
                return n;
            }
            
            // Extract the argument to BytesOf (should be the byte array)
            if (bytesOf.getArguments().size() != 1) {
                return n;
            }
            
            Expression byteArrayExpr = bytesOf.getArguments().get(0);
            
            // Create the replacement: Arrays.toString(byteArray).replaceAll("[\\\\[\\\\], ]", "")
            // MethodCallExpr Arrays.toString(byteArray)
            MethodCallExpr arraysToString = new MethodCallExpr(
                new NameExpr("Arrays"),
                "toString",
                new NodeList<>(byteArrayExpr)
            );
            
            // MethodCallExpr .replaceAll("[\\\\[\\\\], ]", "")
            MethodCallExpr replaceAll = new MethodCallExpr(
                arraysToString,
                "replaceAll",
                new NodeList<>(
                    new StringLiteralExpr("[\\\\\\\\[\\\\\\\\], ]"),
                    new StringLiteralExpr("")
                )
            );
            
            return replaceAll;
        }
    }
}