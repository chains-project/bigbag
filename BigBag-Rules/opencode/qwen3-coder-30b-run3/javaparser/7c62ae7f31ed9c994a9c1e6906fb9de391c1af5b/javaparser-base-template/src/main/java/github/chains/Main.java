package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.PrimitiveType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import java.nio.charset.StandardCharsets;

/**
 * Generic transformation rule to fix breaking changes in Hibernate UserType interface.
 * This fixes the issue with nullSafeSet method signature in UserType implementations.
 * Specifically addresses changes from SessionImplementor to SharedSessionContractImplementor
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the input directory
        String inputDir = args.length > 0 ? args[0] : "/workspace/onebusaway-gtfs-modules";
        
        Files.walk(Paths.get(inputDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(java.nio.file.Path filePath) {
        try {
            String code = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            
            CompilationUnit cu = StaticJavaParser.parse(code);
            
            // Apply transformation
            new HibernateUserTypeFixer().visit(cu, null);
            
            // Write back to file
            Files.write(filePath, cu.toString().getBytes(StandardCharsets.UTF_8));
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that looks for UserType implementations and fixes the nullSafeSet method signature
     */
    private static class HibernateUserTypeFixer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Check if this class implements UserType
            if (n.getImplementedTypes().stream()
                .anyMatch(type -> type.getNameAsString().contains("UserType"))) {
                
                // Look for nullSafeSet method with incorrect signature
                List<MethodDeclaration> methods = n.getMethods();
                for (MethodDeclaration method : methods) {
                    if ("nullSafeSet".equals(method.getNameAsString())) {
                        // Check if the method signature needs to be updated
                        if (method.getParameters().size() == 4) {
                            // Check if last parameter type is SessionImplementor instead of SharedSessionContractImplementor
                            var lastParam = method.getParameter(3);
                            if (lastParam.getType().toString().contains("SessionImplementor")) {
                                // We need to update the method signature
                                updateNullSafeSetMethod(method);
                            }
                        }
                    }
                }
                
                // Also remove outdated SessionImplementor import if present
                removeSessionImplementorImport(n);
            }
        }
        
        private void updateNullSafeSetMethod(MethodDeclaration method) {
            // Modify method signature to use SharedSessionContractImplementor instead of SessionImplementor
            method.getParameter(3).setType(new com.github.javaparser.ast.type.ClassOrInterfaceType("org.hibernate.engine.spi.SharedSessionContractImplementor"));
        }
        
        private void removeSessionImplementorImport(ClassOrInterfaceDeclaration n) {
            // Remove the import statement for SessionImplementor if it exists
            n.getCompilationUnit().ifPresent(cu -> {
                cu.getImports().removeIf(importDecl -> {
                    if (importDecl.isNormalImport()) {
                        String importName = importDecl.getNameAsString();
                        return importName.contains("SessionImplementor") && 
                               importName.contains("hibernate.engine.spi") == false;
                    }
                    return false;
                });
            });
        }
    }
}