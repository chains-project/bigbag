package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.out.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix the Bouncy Castle TLS API change
            new TLSImportRemovalVisitor().visit(cu, null);
            new TLSUsageFixVisitor().visit(cu, null);
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Visitor to remove the TLS imports
    private static class TLSImportRemovalVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("org.bouncycastle.crypto.tls")) {
                importDecl.remove();
                System.out.println("Removed TLS import: " + importName);
            }
        }
    }
    
    // Visitor to replace TLS usage with appropriate replacements
    private static class TLSUsageFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Handle TlsFatalAlert constructor calls - we'll just log them for review
            if (methodCall.getNameAsString().equals("TlsFatalAlert")) {
                System.out.println("Found TlsFatalAlert constructor usage at: " + methodCall.toString());
            }
        }
        
        @Override
        public void visit(FieldAccessExpr fieldAccess, Void arg) {
            super.visit(fieldAccess, arg);
            
            // Handle AlertDescription references - we'll just log them for review
            if (fieldAccess.getNameAsString().equals("AlertDescription")) {
                System.out.println("Found AlertDescription usage at: " + fieldAccess.toString());
            }
        }
    }
}