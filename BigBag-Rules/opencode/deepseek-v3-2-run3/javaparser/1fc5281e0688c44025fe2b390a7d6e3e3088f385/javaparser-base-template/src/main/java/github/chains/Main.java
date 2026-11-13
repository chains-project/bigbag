package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
    }
    
    private static boolean transformFile(Path javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        if (cu == null) {
            return false;
        }
        
        final boolean[] modified = new boolean[1];
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importStr = importDecl.getNameAsString();
            
            // Transform SelectChannelConnector import
            if (importStr.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                importDecl.setName("org.eclipse.jetty.server.ServerConnector");
                modified[0] = true;
            }
            
            // Transform javax.servlet to jakarta.servlet
            if (importStr.startsWith("javax.servlet")) {
                String newImport = importStr.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImport);
                modified[0] = true;
            }
        }
        
        // Find and transform ObjectCreationExpr for SelectChannelConnector
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            if (expr.getType().asString().equals("SelectChannelConnector")) {
                expr.setType("ServerConnector");
                modified[0] = true;
            }
        });
        
        // Find and remove MethodCallExpr for setSendServerVersion and setSendDateHeader
        List<MethodCallExpr> toRemove = new ArrayList<>();
        cu.findAll(MethodCallExpr.class).forEach(expr -> {
            String methodName = expr.getNameAsString();
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                toRemove.add(expr);
                modified[0] = true;
            }
        });
        
        // Remove the method calls by removing their parent ExpressionStmt
        for (MethodCallExpr expr : toRemove) {
            expr.getParentNode().ifPresent(parent -> {
                if (parent instanceof com.github.javaparser.ast.stmt.ExpressionStmt) {
                    parent.remove();
                }
            });
        }
        
        if (modified[0]) {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String transformedCode = printer.print(cu);
            Files.write(javaFile, transformedCode.getBytes());
            return true;
        }
        
        return false;
    }
}