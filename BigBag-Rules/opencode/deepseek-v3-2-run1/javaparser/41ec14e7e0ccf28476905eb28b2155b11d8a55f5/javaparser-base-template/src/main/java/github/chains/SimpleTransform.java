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
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SimpleTransform {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... github.chains.SimpleTransform <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> transformFile(path));
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path filePath) {
        try {
            System.out.println("Processing: " + filePath);
            
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            
            // Remove javax.validation and commons-beanutils imports
            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.startsWith("javax.validation") || 
                    importName.startsWith("org.apache.commons.beanutils")) {
                    importsToRemove.add(importDecl);
                    modified = true;
                    System.out.println("  Removing import: " + importName);
                }
            }
            cu.getImports().removeAll(importsToRemove);
            
            // Transform the AST
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
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Look for methods that use javax.validation or PropertyUtils
            BlockStmt body = n.getBody().orElse(null);
            if (body != null) {
                List<Statement> statements = body.getStatements();
                List<Statement> newStatements = new ArrayList<>();
                
                for (Statement stmt : statements) {
                    String stmtStr = stmt.toString();
                    if (stmtStr.contains("PropertyUtils.") || 
                        stmtStr.contains("Validator ") ||
                        stmtStr.contains("BeanDescriptor ") ||
                        stmtStr.contains("ElementDescriptor ") ||
                        stmtStr.contains("ConstraintDescriptor ") ||
                        stmtStr.contains("NotNull") ||
                        stmtStr.contains("NotEmpty") ||
                        stmtStr.contains("NotBlank")) {
                        
                        // Comment out problematic statements
                        System.out.println("  Commenting out statement using javax.validation or PropertyUtils: " + 
                                          stmtStr.substring(0, Math.min(50, stmtStr.length())) + "...");
                        modified = true;
                        
                        // Replace with a comment
                        newStatements.add(new ExpressionStmt(
                            new NameExpr("/* TODO: javax.validation and commons-beanutils are no longer available in pm-wicket-utils 5.0. " +
                                       "Add explicit dependencies or refactor. Original code was: " + 
                                       stmtStr.replace("\n", " ").substring(0, Math.min(100, stmtStr.length())) + " */")
                        ));
                    } else {
                        newStatements.add(stmt);
                    }
                }
                
                if (modified && !newStatements.equals(statements)) {
                    body.setStatements(newStatements);
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Handle HibernateValidatorProperty.validatorFactory.getValidator()
            if (n.getScope().isNameExpr()) {
                String scopeName = n.getScope().asNameExpr().getNameAsString();
                String fieldName = n.getNameAsString();
                
                if ("HibernateValidatorProperty".equals(scopeName) && 
                    "validatorFactory".equals(fieldName)) {
                    System.out.println("  Found HibernateValidatorProperty.validatorFactory usage");
                    modified = true;
                    
                    // Replace with commented version
                    return new NameExpr("/* HibernateValidatorProperty.validatorFactory - REMOVED: javax.validation no longer available */ null");
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Handle PropertyUtils method calls
            if (n.getScope().isPresent() && n.getScope().get().isNameExpr()) {
                String scopeName = n.getScope().get().asNameExpr().getNameAsString();
                if ("PropertyUtils".equals(scopeName)) {
                    System.out.println("  Found PropertyUtils." + n.getNameAsString() + " usage");
                    modified = true;
                    
                    // Create a simple Java Reflection replacement
                    String methodName = n.getNameAsString();
                    if ("getPropertyDescriptors".equals(methodName) && n.getArguments().size() == 1) {
                        // Replace PropertyUtils.getPropertyDescriptors(class) with 
                        // Introspector.getBeanInfo(class).getPropertyDescriptors()
                        MethodCallExpr introspectorCall = new MethodCallExpr(
                            new NameExpr("java.beans.Introspector"),
                            "getBeanInfo",
                            n.getArguments()
                        );
                        return new MethodCallExpr(introspectorCall, "getPropertyDescriptors");
                    } else if ("getPropertyDescriptor".equals(methodName) && n.getArguments().size() == 2) {
                        // This is harder to replace with simple reflection
                        // We'll comment it out for now
                        return new MethodCallExpr(
                            new NameExpr("/* PropertyUtils.getPropertyDescriptor - REPLACE with reflection */ null"),
                            "getPropertyDescriptor",
                            n.getArguments()
                        );
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}