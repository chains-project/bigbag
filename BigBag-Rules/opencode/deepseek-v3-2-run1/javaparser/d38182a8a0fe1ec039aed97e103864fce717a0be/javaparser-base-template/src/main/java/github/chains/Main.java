package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    // Configuration for the specific breaking change
    private static final String OLD_QUALIFIED_NAME = "com.artipie.http.auth.Authentication.User";
    private static final String NEW_QUALIFIED_NAME = "com.artipie.http.auth.AuthUser";
    private static final String OLD_OUTER_CLASS = "Authentication";
    private static final String OLD_INNER_CLASS = "User";
    private static final String NEW_CLASS = "AuthUser";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Transformation: " + OLD_QUALIFIED_NAME + " -> " + NEW_QUALIFIED_NAME);
            System.err.println("Constructor: " + OLD_INNER_CLASS + "(String) -> " + NEW_CLASS + "(String, String)");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: " + OLD_QUALIFIED_NAME + " -> " + NEW_QUALIFIED_NAME);
        processDirectory(sourceDir.toFile());
        System.out.println("Transformation completed successfully!");
    }
    
    private static void processDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file);
            } else if (file.getName().endsWith(".java")) {
                processJavaFile(file);
            }
        }
    }
    
    private static void processJavaFile(File file) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        try {
            cu = parser.parse(file).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found: " + file, e);
        }
        
        final boolean[] modified = {false};
        
        // 1. Update imports
        boolean needsAuthUserImport = false;
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(OLD_QUALIFIED_NAME)) {
                imp.setName(NEW_QUALIFIED_NAME);
                modified[0] = true;
                System.out.println("Updated import in " + file + ": " + imp.getNameAsString() + " -> " + NEW_QUALIFIED_NAME);
            }
            if (imp.getNameAsString().equals("com.artipie.http.auth.Authentication")) {
                needsAuthUserImport = true;
            }
        }
        
        // Add AuthUser import if Authentication is imported
        if (needsAuthUserImport) {
            boolean hasAuthUserImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(NEW_QUALIFIED_NAME));
            if (!hasAuthUserImport) {
                cu.addImport("com.artipie.http.auth.AuthUser");
                modified[0] = true;
                System.out.println("Added import in " + file + ": " + NEW_QUALIFIED_NAME);
            }
        } else {
            // Also check if we're using AuthUser in the code (type references changed)
            // If so, we need to add the import
            boolean[] usesAuthUser = {false};
            cu.walk(ClassOrInterfaceType.class, type -> {
                if (type.getNameAsString().equals(NEW_CLASS) && !type.getScope().isPresent()) {
                    usesAuthUser[0] = true;
                }
            });
            if (usesAuthUser[0]) {
                boolean hasAuthUserImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals(NEW_QUALIFIED_NAME));
                if (!hasAuthUserImport) {
                    cu.addImport("com.artipie.http.auth.AuthUser");
                    modified[0] = true;
                    System.out.println("Added import (type usage) in " + file + ": " + NEW_QUALIFIED_NAME);
                }
            }
        }
        
        // 2. Update type references and constructor calls
        cu.walk(Node.TreeTraversal.POSTORDER, node -> {
            // Update type references like Authentication.User to AuthUser
            if (node instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) node;
                if (type.getNameAsString().equals(OLD_INNER_CLASS)) {
                    // Check if it's qualified as Authentication.User
                    Optional<ClassOrInterfaceType> scope = type.getScope();
                    if (scope.isPresent() && scope.get().getNameAsString().equals(OLD_OUTER_CLASS)) {
                        type.setName(NEW_CLASS);
                        type.setScope(null); // Remove outer class prefix
                        modified[0] = true;
                    }
                }
            }
            
            // Update constructor calls: new Authentication.User(...) -> new AuthUser(...)
            if (node instanceof ObjectCreationExpr) {
                ObjectCreationExpr expr = (ObjectCreationExpr) node;
                if (expr.getType().getNameAsString().equals(OLD_INNER_CLASS)) {
                    Optional<ClassOrInterfaceType> scope = expr.getType().getScope();
                    if (scope.isPresent() && scope.get().getNameAsString().equals(OLD_OUTER_CLASS)) {
                        // Change type to AuthUser
                        expr.getType().setName(NEW_CLASS);
                        expr.getType().setScope(null);
                        
                        // Check constructor arguments
                        NodeList<Expression> args = expr.getArguments();
                        if (args.size() == 1) {
                            // Old: new Authentication.User(username)
                            // New: new AuthUser(username, username) - auth context defaults to username
                            // We need to add the auth context parameter (same as username)
                            Expression usernameArg = args.get(0);
                            // Create a copy of the expression
                            if (usernameArg instanceof StringLiteralExpr) {
                                // If it's a string literal, create a new one with same value
                                args.add(new StringLiteralExpr(((StringLiteralExpr) usernameArg).getValue()));
                            } else {
                                // For other expressions, try to clone
                                args.add(usernameArg.clone());
                            }
                        }
                        // If already 2 args, leave as is (might be already updated)
                        modified[0] = true;
                    }
                }
            }
        });
        
        if (modified[0]) {
            // Always add AuthUser import if we're modifying the file
            boolean hasAuthUserImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(NEW_QUALIFIED_NAME));
            if (!hasAuthUserImport) {
                cu.addImport("com.artipie.http.auth.AuthUser");
                System.out.println("Added AuthUser import to " + file);
            }
            
            // Write back the modified file using JavaParser's built-in printer
            Files.write(file.toPath(), cu.toString().getBytes());
            System.out.println("Updated file: " + file);
        }
    }
}