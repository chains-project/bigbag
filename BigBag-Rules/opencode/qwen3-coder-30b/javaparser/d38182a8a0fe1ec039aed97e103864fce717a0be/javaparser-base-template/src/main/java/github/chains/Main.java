package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Generic transformation rule to fix Authentication.User -> AuthUser breaking change.
 * This rule transforms code that used Authentication.User to use AuthUser directly.
 * 
 * Example transformation:
 * Before: Authentication.User user = new Authentication.User("name", "pass");
 * After:  AuthUser user = new AuthUser("name", "pass");
 * 
 * Also transforms:
 * Before: Authentication.User.ALICE
 * After:  AuthUser.ALICE
 * 
 * This rule is generic and can be applied to any project affected by this breaking change.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        File directory = new File(sourceDir);
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        processDirectory(directory);
        System.out.println("Transformation completed successfully.");
    }
    
    private static void processDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file);
            } else if (file.getName().endsWith(".java")) {
                processFile(file);
            }
        }
    }
    
    private static void processFile(File file) throws IOException {
        // Read the source file
        String source = new String(Files.readAllBytes(file.toPath()));
        
        // Parse the compilation unit
        CompilationUnit cu = StaticJavaParser.parse(source);
        
        // Create visitor to transform the code
        TransformationVisitor visitor = new TransformationVisitor();
        visitor.visit(cu, null);
        
        // Write the transformed code back to the file
        Files.write(file.toPath(), cu.toString().getBytes());
    }
    
    private static class TransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ClassOrInterfaceType type, Void arg) {
            super.visit(type, arg);
            
            // Transform Authentication.User to AuthUser
            if (type.getName().asString().equals("User") && 
                type.getScope().isPresent() && 
                type.getScope().get().asString().equals("Authentication")) {
                type.setName("AuthUser");
                type.setScope(null);
            }
        }
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            
            // Transform new Authentication.User(...) to new AuthUser(...)
            if (expr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) expr.getType();
                if (type.getName().asString().equals("User") && 
                    type.getScope().isPresent() && 
                    type.getScope().get().asString().equals("Authentication")) {
                    type.setName("AuthUser");
                    type.setScope(null);
                }
            }
        }
        
        @Override
        public void visit(FieldAccessExpr expr, Void arg) {
            super.visit(expr, arg);
            
            // Transform Authentication.User to AuthUser in field access
            if (expr.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) expr.getScope();
                if (scope.getName().asString().equals("Authentication") && 
                    expr.getName().asString().equals("User")) {
                    expr.setName("AuthUser");
                    expr.setScope(new NameExpr("AuthUser"));
                }
            }
        }
    }
}