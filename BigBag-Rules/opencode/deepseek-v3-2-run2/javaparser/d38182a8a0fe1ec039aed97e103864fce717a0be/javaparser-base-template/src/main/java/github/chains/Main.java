package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Transforming files in: " + sourceDir);
        
        try {
            transformDirectory(sourceDir.toFile());
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(File directory) throws FileNotFoundException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Source directory does not exist: " + directory);
        }
        
        JavaParser parser = new JavaParser();
        
        // Process all Java files recursively
        processFiles(directory, parser);
    }
    
    private static void processFiles(File dir, JavaParser parser) throws FileNotFoundException {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                processFiles(file, parser);
            } else if (file.getName().endsWith(".java")) {
                transformFile(file, parser);
            }
        }
    }
    
    private static void transformFile(File file, JavaParser parser) throws FileNotFoundException {
        System.out.println("Processing: " + file.getPath());
        
        Optional<CompilationUnit> cuOpt = parser.parse(file).getResult();
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse: " + file.getPath());
            return;
        }
        
        CompilationUnit cu = cuOpt.get();
        
        // First, check if we need to add AuthUser import
        boolean hasAuthenticationImport = false;
        boolean hasAuthUserImport = false;
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importStr = importDecl.getNameAsString();
            if (importStr.equals("com.artipie.http.auth.Authentication")) {
                hasAuthenticationImport = true;
            }
            if (importStr.equals("com.artipie.http.auth.AuthUser")) {
                hasAuthUserImport = true;
            }
        }
        
        // Create visitor to transform Authentication.User to AuthUser
        ModifierVisitor<Void> visitor = new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceType type, Void arg) {
                // Transform type references: Authentication.User -> AuthUser
                if (type.getNameAsString().equals("User") && type.getScope().isPresent()) {
                    String scope = type.getScope().get().asString();
                    if (scope.equals("Authentication")) {
                        // Replace Authentication.User with AuthUser
                        return new ClassOrInterfaceType(null, "AuthUser");
                    }
                }
                return super.visit(type, arg);
            }
            
            @Override
            public Visitable visit(ObjectCreationExpr expr, Void arg) {
                // Transform constructor calls: new Authentication.User(...) -> new AuthUser(...)
                if (expr.getType().getNameAsString().equals("User") && expr.getType().getScope().isPresent()) {
                    String scope = expr.getType().getScope().get().asString();
                    if (scope.equals("Authentication")) {
                        // Get the single argument (name)
                        if (expr.getArguments().size() == 1) {
                            // new Authentication.User(name) -> new AuthUser(name, "test")
                            ObjectCreationExpr newExpr = new ObjectCreationExpr();
                            newExpr.setType(new ClassOrInterfaceType(null, "AuthUser"));
                            newExpr.addArgument(expr.getArgument(0).clone());
                            newExpr.addArgument(new StringLiteralExpr("test"));
                            return newExpr;
                        }
                    }
                }
                return super.visit(expr, arg);
            }
        };
        
        cu.accept(visitor, null);
        
        // Add AuthUser import if needed (after transformation)
        if (hasAuthenticationImport && !hasAuthUserImport) {
            cu.addImport("com.artipie.http.auth.AuthUser");
        }
        
        // Write transformed file
        try {
            System.out.println("Writing transformed file: " + file.getPath());
            // Write the transformed content back to the file
            try (PrintWriter out = new PrintWriter(file)) {
                out.println(cu.toString());
            }
            System.out.println("Successfully transformed: " + file.getPath());
        } catch (Exception e) {
            System.err.println("Failed to write transformed file: " + file.getPath());
            e.printStackTrace();
        }
    }
}