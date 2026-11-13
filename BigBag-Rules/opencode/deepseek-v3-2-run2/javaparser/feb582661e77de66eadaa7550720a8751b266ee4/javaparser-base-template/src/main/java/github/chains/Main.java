package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.comments.LineComment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration parameters - these would be externalized for a generic solution
    private static final String OLD_STRINGUTILS_IMPORT = "liquibase.util.StringUtils";
    private static final String NEW_STRINGUTILS_IMPORT = "liquibase.repackaged.org.apache.commons.lang3.StringUtils";
    
    private static final String EXECUTOR_SERVICE_CLASS = "ExecutorService";
    private static final String EXECUTOR_SERVICE_GET_INSTANCE = "getInstance";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try {
            cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) {
                return false;
            }
        } catch (Exception e) {
            System.err.println("Failed to parse " + javaFile + ": " + e.getMessage());
            return false;
        }
        
        boolean modified = false;
        boolean needsScopeImport = false;
        
        // Fix StringUtils imports (exact match)
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals(OLD_STRINGUTILS_IMPORT)) {
                importDecl.setName(NEW_STRINGUTILS_IMPORT);
                modified = true;
                System.out.println("Fixed StringUtils import in " + javaFile);
            }
        }
        
        // Collect all method calls to modify
        List<MethodCallExpr> callsToModify = new ArrayList<>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals(EXECUTOR_SERVICE_GET_INSTANCE)) {
                    if (n.getScope().isPresent()) {
                        Expression scope = n.getScope().get();
                        String scopeName = scope.toString();
                        boolean isExecutorService = scopeName.equals(EXECUTOR_SERVICE_CLASS) || 
                            scopeName.endsWith("." + EXECUTOR_SERVICE_CLASS) ||
                            scopeName.equals("liquibase.executor.ExecutorService");
                        
                        if (isExecutorService) {
                            callsToModify.add(n);
                        }
                    }
                }
                super.visit(n, arg);
            }
        }, null);
        
        // Modify the collected calls
        for (MethodCallExpr call : callsToModify) {
            System.out.println("Found ExecutorService.getInstance() to modify");
            
            // Create replacement
            MethodCallExpr getSingletonCall = new MethodCallExpr(
                new MethodCallExpr(
                    new NameExpr("Scope"),
                    "getCurrentScope"
                ),
                "getSingleton"
            );
            
            getSingletonCall.addArgument(
                new FieldAccessExpr(
                    new NameExpr("ExecutorService"),
                    "class"
                )
            );
            
            // Replace in parent if parent is a MethodCallExpr
            if (call.getParentNode().isPresent() && call.getParentNode().get() instanceof MethodCallExpr) {
                MethodCallExpr parent = (MethodCallExpr) call.getParentNode().get();
                parent.setScope(getSingletonCall);
            } else {
                call.replace(getSingletonCall);
            }
            
            modified = true;
            needsScopeImport = true;
        }
        
        if (!callsToModify.isEmpty()) {
            System.out.println("Fixed " + callsToModify.size() + " ExecutorService.getInstance() calls in " + javaFile);
        }
        
        // Add Scope import if needed
        if (needsScopeImport && !hasImport(cu, "liquibase.Scope")) {
            cu.addImport("liquibase.Scope");
            System.out.println("Added Scope import to " + javaFile);
        }
        
        if (modified) {
            String newContent = cu.toString();
            System.out.println("Writing modified content to: " + javaFile);
            // Check if the transformation actually worked
            if (newContent.contains("Scope.getCurrentScope().getSingleton(ExecutorService.class)")) {
                System.out.println("SUCCESS: Transformation appears in output!");
            } else {
                System.out.println("WARNING: Transformation not found in output!");
                System.out.println("Output preview: " + newContent);
            }
            Files.write(javaFile, newContent.getBytes());
        }
        
        return modified;
    }
    
    private static boolean hasImport(CompilationUnit cu, String importName) {
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals(importName)) {
                return true;
            }
        }
        return false;
    }
    
    
}