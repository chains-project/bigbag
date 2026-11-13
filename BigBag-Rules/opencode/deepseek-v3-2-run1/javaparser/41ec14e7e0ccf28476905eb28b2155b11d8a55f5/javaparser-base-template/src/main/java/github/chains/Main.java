package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> transformFile(path));
            
            System.out.println("Transformation complete!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            System.out.println("Processing: " + filePath);
            
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            
            // Remove javax.validation imports
            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.startsWith("javax.validation") || 
                    importName.startsWith("org.apache.commons.beanutils")) {
                    importsToRemove.add(importDecl);
                    modified = true;
                }
            }
            cu.getImports().removeAll(importsToRemove);
            
            // Transform code that uses javax.validation and commons-beanutils
            ValidationRemovalVisitor visitor = new ValidationRemovalVisitor();
            cu.accept(visitor, null);
            modified = modified || visitor.isModified();
            
            if (modified) {
                // Write back the transformed file
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("  -> Modified: " + filePath);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class ValidationRemovalVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check for HibernateValidatorProperty.validatorFactory usage
            if (n.getScope().isNameExpr()) {
                String scopeName = n.getScope().asNameExpr().getNameAsString();
                String fieldName = n.getNameAsString();
                
                if ("HibernateValidatorProperty".equals(scopeName) && 
                    "validatorFactory".equals(fieldName)) {
                    // Replace with a placeholder or remove
                    System.out.println("  Found HibernateValidatorProperty.validatorFactory usage");
                    modified = true;
                    
                    // Create a commented-out version
                    return new NameExpr("/* HibernateValidatorProperty.validatorFactory - REMOVED: javax.validation no longer available */ null");
                }
            }
            return super.visit(n, arg);
        }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for PropertyUtils method calls
            if (n.getScope().isPresent()) {
                if (n.getScope().get().isNameExpr()) {
                    String scopeName = n.getScope().get().asNameExpr().getNameAsString();
                    if ("PropertyUtils".equals(scopeName)) {
                        System.out.println("  Found PropertyUtils." + n.getNameAsString() + " usage");
                        modified = true;
                        
                        // Replace with alternative using Java Reflection or comment out
                        String methodName = n.getNameAsString();
                        if ("getPropertyDescriptors".equals(methodName)) {
                            // Replace with Java Reflection alternative
                            return new MethodCallExpr(
                                new NameExpr("ReflectionUtils"),
                                "getPropertyDescriptors",
                                n.getArguments()
                            );
                        } else if ("getPropertyDescriptor".equals(methodName)) {
                            // Replace with Java Reflection alternative  
                            return new MethodCallExpr(
                                new NameExpr("ReflectionUtils"),
                                "getPropertyDescriptor",
                                n.getArguments()
                            );
                        }
                    }
                }
            }
            
            // Check for Validator, BeanDescriptor, etc. constructor calls
            String typeName = getTypeNameFromExpression(n);
            if (typeName != null && (
                typeName.contains("Validator") ||
                typeName.contains("BeanDescriptor") ||
                typeName.contains("ElementDescriptor") ||
                typeName.contains("ConstraintDescriptor") ||
                "NotNull".equals(typeName) ||
                "NotEmpty".equals(typeName) ||
                "NotBlank".equals(typeName))) {
                
                System.out.println("  Found " + typeName + " usage");
                modified = true;
                
                // Comment out or replace with null
                return new NameExpr("/* " + typeName + " - REMOVED: javax.validation no longer available */ null");
            }
            
            return super.visit(n, arg);
        }
        
        private String getTypeNameFromExpression(MethodCallExpr expr) {
            // Try to determine the type being instantiated or called
            if (expr.getNameAsString().equals("getValidator") && 
                expr.getScope().isPresent() &&
                expr.getScope().get().isFieldAccessExpr()) {
                
                FieldAccessExpr scope = expr.getScope().get().asFieldAccessExpr();
                if (scope.getNameAsString().equals("validatorFactory")) {
                    return "Validator";
                }
            }
            
            // Check for instanceof expressions in the code
            // This is a simplified check
            return null;
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Check for statements that use javax.validation types
            if (n.getExpression().isMethodCallExpr()) {
                MethodCallExpr call = n.getExpression().asMethodCallExpr();
                if (call.getScope().isPresent() && 
                    call.getScope().get().isNameExpr()) {
                    
                    String scopeName = call.getScope().get().asNameExpr().getNameAsString();
                    if ("validator".equals(scopeName) && 
                        call.getNameAsString().equals("getConstraintsForClass")) {
                        
                        System.out.println("  Found validator.getConstraintsForClass() usage");
                        modified = true;
                        
                        // Replace with empty statement or comment
                        return new ExpressionStmt(
                            new NameExpr("/* Validation code removed: javax.validation no longer available */")
                        );
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Add ReflectionUtils helper if needed
            boolean[] hasPropertyUtilsUsage = new boolean[]{false};
            
            // Check if this class uses PropertyUtils
            n.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(MethodCallExpr call, Void arg) {
                    if (call.getScope().isPresent() && 
                        call.getScope().get().isNameExpr() &&
                        "PropertyUtils".equals(call.getScope().get().asNameExpr().getNameAsString())) {
                        hasPropertyUtilsUsage[0] = true;
                    }
                    return super.visit(call, arg);
                }
            }, null);
            
            if (hasPropertyUtilsUsage[0] && !n.getNameAsString().equals("ReflectionUtils")) {
                // Add a helper method for PropertyUtils replacements
                System.out.println("  Adding ReflectionUtils helper to " + n.getNameAsString());
                
                // Create helper methods
                MethodDeclaration getPropertyDescriptorsMethod = new MethodDeclaration();
                getPropertyDescriptorsMethod.setPublic(true)
                    .setStatic(true)
                    .setName("getPropertyDescriptors")
                    .setType("java.beans.PropertyDescriptor[]");
                // ... implementation would be added here
                
                modified = true;
            }
            
            return super.visit(n, arg);
        }
    }
}