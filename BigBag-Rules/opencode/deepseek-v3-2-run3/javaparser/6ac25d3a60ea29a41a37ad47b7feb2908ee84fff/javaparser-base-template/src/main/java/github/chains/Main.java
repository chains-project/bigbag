package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser parser = new JavaParser();
        int totalChanges = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean fileChanged = false;
                
                // Transformations for jakarta.servlet-api 6.0.0 breaking changes
                fileChanged |= removeHttpSessionContextImports(cu);
                fileChanged |= removeDeprecatedHttpSessionMethods(cu);
                fileChanged |= updateErrorAttributeNames(cu);
                
                if (fileChanged) {
                    Files.write(javaFile, cu.toString().getBytes());
                    totalChanges++;
                    System.out.println("Updated: " + javaFile);
                }
            } catch (IOException e) {
                System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("Total files updated: " + totalChanges);
    }
    
    /**
     * Remove imports of jakarta.servlet.http.HttpSessionContext which was removed in 6.0.0
     */
    private static boolean removeHttpSessionContextImports(CompilationUnit cu) {
        final boolean[] changed = {false};
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration importDecl = imports.get(i);
            String importName = importDecl.getNameAsString();
            
            if (importName.equals("jakarta.servlet.http.HttpSessionContext") || 
                importName.equals("javax.servlet.http.HttpSessionContext")) {
                importDecl.remove();
                changed[0] = true;
                System.out.println("  Removed import: " + importName);
            }
        }
        
        return changed[0];
    }
    
    /**
     * Remove deprecated HttpSession methods that were removed in 6.0.0:
     * - getSessionContext()
     * - getValue(String)
     * - getValueNames()
     * - putValue(String, Object)
     * - removeValue(String)
     */
    private static boolean removeDeprecatedHttpSessionMethods(CompilationUnit cu) {
        final boolean[] changed = {false};
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                super.visit(n, arg);
                
                // Check if this class implements HttpSession
                boolean implementsHttpSession = n.getImplementedTypes().stream()
                    .anyMatch(type -> {
                        String typeName = type.getNameAsString();
                        return typeName.equals("HttpSession") || 
                               typeName.equals("jakarta.servlet.http.HttpSession") ||
                               typeName.equals("javax.servlet.http.HttpSession");
                    });
                
                if (implementsHttpSession) {
                    List<MethodDeclaration> methodsToRemove = new ArrayList<>();
                    
                    for (MethodDeclaration method : n.getMethods()) {
                        String methodName = method.getNameAsString();
                        boolean isDeprecated = method.getAnnotations().stream()
                            .anyMatch(ann -> ann.getNameAsString().equals("Deprecated"));
                        
                        // Check for deprecated HttpSession methods
                        if ((methodName.equals("getSessionContext") && method.getParameters().isEmpty()) ||
                            (methodName.equals("getValue") && method.getParameters().size() == 1) ||
                            (methodName.equals("getValueNames") && method.getParameters().isEmpty()) ||
                            (methodName.equals("putValue") && method.getParameters().size() == 2) ||
                            (methodName.equals("removeValue") && method.getParameters().size() == 1)) {
                            
                            // Only remove if marked with @Deprecated or has @Override annotation
                            if (isDeprecated || method.isAnnotationPresent("Override")) {
                                methodsToRemove.add(method);
                            }
                        }
                    }
                    
                    for (MethodDeclaration method : methodsToRemove) {
                        method.remove();
                        changed[0] = true;
                        System.out.println("  Removed deprecated method: " + method.getNameAsString());
                    }
                }
            }
        }, null);
        
        return changed[0];
    }
    
    /**
     * Update error attribute names from javax.servlet.error.* to jakarta.servlet.error.*
     */
    private static boolean updateErrorAttributeNames(CompilationUnit cu) {
        final boolean[] changed = {false};
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check for setAttribute/getAttribute calls with error attribute names
                String methodName = n.getNameAsString();
                if (methodName.equals("setAttribute") || methodName.equals("getAttribute") || 
                    methodName.equals("get")) {
                    
                    if (n.getArguments().size() > 0) {
                        Node firstArg = n.getArgument(0);
                        if (firstArg instanceof StringLiteralExpr) {
                            StringLiteralExpr stringLiteral = (StringLiteralExpr) firstArg;
                            String value = stringLiteral.getValue();
                            
                            if (value.startsWith("javax.servlet.error.")) {
                                String newValue = value.replace("javax.servlet.error.", "jakarta.servlet.error.");
                                stringLiteral.setValue(newValue);
                                changed[0] = true;
                                System.out.println("  Updated error attribute: " + value + " -> " + newValue);
                            }
                        }
                    }
                }
            }
            
            @Override
            public void visit(StringLiteralExpr n, Void arg) {
                super.visit(n, arg);
                
                // Also check string literals anywhere in the code
                String value = n.getValue();
                if (value.startsWith("javax.servlet.error.")) {
                    String newValue = value.replace("javax.servlet.error.", "jakarta.servlet.error.");
                    n.setValue(newValue);
                    changed[0] = true;
                    System.out.println("  Updated error attribute in string literal: " + value + " -> " + newValue);
                }
            }
        }, null);
        
        return changed[0];
    }
}