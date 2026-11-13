package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void transformFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + file)
            );
            
            Node result = (Node) cu.accept(new AuthenticationUserToAuthUserVisitor(), null);
            boolean modified = result != cu;
            
            if (modified) {
                Files.write(file, cu.toString().getBytes());
                System.out.println("Modified: " + file);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
    
    private static class AuthenticationUserToAuthUserVisitor extends ModifierVisitor<Void> {
        private boolean needsAuthUserImport = false;
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Check if we're importing Authentication.User (which doesn't exist anymore)
            String importName = importDecl.getNameAsString();
            if (importName.equals("com.artipie.http.auth.Authentication.User")) {
                // Replace with AuthUser import
                return new ImportDeclaration("com.artipie.http.auth.AuthUser", importDecl.isStatic(), importDecl.isAsterisk());
            }
            
            // Check if we already import AuthUser
            if (importName.equals("com.artipie.http.auth.AuthUser")) {
                needsAuthUserImport = true;
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            // Replace Authentication.User type references with AuthUser
            if (type.getNameAsString().equals("User") && type.getScope().isPresent()) {
                Node scope = type.getScope().get();
                if (scope instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType scopeType = (ClassOrInterfaceType) scope;
                    if (scopeType.getNameAsString().equals("Authentication")) {
                        // Mark that we need AuthUser import
                        needsAuthUserImport = true;
                        // Replace Authentication.User with AuthUser
                        return new ClassOrInterfaceType(null, "AuthUser");
                    }
                }
            }
            
            // Also handle direct AuthUser references
            if (type.getNameAsString().equals("AuthUser")) {
                needsAuthUserImport = true;
                // Ensure AuthUser has no qualifying scope
                if (type.getScope().isPresent()) {
                    return new ClassOrInterfaceType(null, "AuthUser");
                }
            }
            
            return (Node) super.visit(type, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Replace new Authentication.User(...) with new AuthUser(...)
            if (expr.getType().getNameAsString().equals("User") && expr.getType().getScope().isPresent()) {
                Node scope = expr.getType().getScope().get();
                if (scope instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType scopeType = (ClassOrInterfaceType) scope;
                    if (scopeType.getNameAsString().equals("Authentication")) {
                        // Mark that we need AuthUser import
                        needsAuthUserImport = true;
                        // Create new AuthUser constructor call
                        // AuthUser has 2 parameters: (String name, String authContext)
                        // Old Authentication.User had 1 parameter: (String name)
                        // We need to add empty string as second parameter
                        ObjectCreationExpr newExpr = new ObjectCreationExpr();
                        newExpr.setType(new ClassOrInterfaceType(null, "AuthUser"));
                        if (expr.getArguments().size() == 1) {
                            // Keep the first argument and add empty string as second
                            newExpr.getArguments().addAll(expr.getArguments());
                            newExpr.addArgument(new StringLiteralExpr(""));
                        } else {
                            // Just copy arguments (unlikely case)
                            newExpr.setArguments(expr.getArguments());
                        }
                        newExpr.setTypeArguments(expr.getTypeArguments().orElse(null));
                        
                        // Preserve any comments
                        expr.getComment().ifPresent(newExpr::setComment);
                        
                        return newExpr;
                    }
                }
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(CompilationUnit cu, Void arg) {
            // Reset flag for each file
            needsAuthUserImport = false;
            
            // Visit children first
            Node result = (Node) super.visit(cu, arg);
            
            // After visiting all nodes, check if we need to add AuthUser import
            System.out.println("DEBUG: needsAuthUserImport=" + needsAuthUserImport + ", hasAuthUserImport=" + hasAuthUserImport(cu));
            if (needsAuthUserImport && !hasAuthUserImport(cu)) {
                // Add import for AuthUser
                cu.addImport("com.artipie.http.auth.AuthUser", false, false);
                System.out.println("Added AuthUser import");
            }
            
            return result;
        }
        
        private boolean hasAuthUserImport(CompilationUnit cu) {
            return cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("com.artipie.http.auth.AuthUser"));
        }
    }
}