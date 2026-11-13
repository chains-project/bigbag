package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws Exception {
        File dir = directory.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory);
        }
        
        List<File> javaFiles = findAllJavaFiles(dir);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int modifiedFiles = 0;
        
        for (File javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
                LogbackTransformationVisitor visitor = new LogbackTransformationVisitor();
                CompilationUnit modifiedCu = (CompilationUnit) cu.accept(visitor, null);
                
                if (visitor.isModified()) {
                    // Write the modified file back
                    Files.write(javaFile.toPath(), modifiedCu.toString().getBytes());
                    System.out.println("Modified: " + javaFile.getAbsolutePath());
                    modifiedFiles++;
                }
            } catch (FileNotFoundException e) {
                System.err.println("Error parsing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("Total files modified: " + modifiedFiles);
    }
    
    private static List<File> findAllJavaFiles(File dir) throws Exception {
        List<File> javaFiles = new ArrayList<>();
        Files.walk(dir.toPath())
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> javaFiles.add(p.toFile()));
        return javaFiles;
    }
    
    static class LogbackTransformationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            // Check for imports of ch.qos.logback.classic.Logger
            if (n.getNameAsString().equals("ch.qos.logback.classic.Logger")) {
                System.out.println("WARNING: Found import of ch.qos.logback.classic.Logger which may cause issues with SLF4J 1.x compatibility");
                System.out.println("  Consider using org.slf4j.Logger instead if not using logback-specific methods");
                modified = true;
            }
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(VariableDeclarationExpr n, Void arg) {
            // Check for variable declarations of type ch.qos.logback.classic.Logger
            for (VariableDeclarator var : n.getVariables()) {
                if (var.getType() instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType type = (ClassOrInterfaceType) var.getType();
                    if ("Logger".equals(type.getNameAsString()) || 
                        "ch.qos.logback.classic.Logger".equals(type.getNameAsString())) {
                        System.out.println("Found Logger variable declaration: " + var.getName());
                        // This variable is declared as Logger type
                        // If it's initialized with LoggerFactory.getLogger(), we might have issues
                        modified = true;
                    }
                }
            }
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(CastExpr n, Void arg) {
            // Check if this is a cast to ch.qos.logback.classic.Logger
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                String typeName = type.getNameAsString();
                
                // Check for Logger cast (could be ch.qos.logback.classic.Logger or just Logger)
                boolean isLoggerCast = "Logger".equals(typeName) || 
                                      "ch.qos.logback.classic.Logger".equals(type.getNameAsString());
                
                if (isLoggerCast) {
                    // Check if the expression being cast is LoggerFactory.getLogger()
                    if (n.getExpression() instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) n.getExpression();
                        if (methodCall.getNameAsString().equals("getLogger")) {
                            // This is a pattern: (Logger) LoggerFactory.getLogger(...)
                            // Remove the cast - let Java handle the assignment compatibility
                            System.out.println("Removing problematic Logger cast: " + n);
                            modified = true;
                            return n.getExpression();
                        }
                    }
                }
            }
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            // Check for calls to logback-specific methods
            String methodName = n.getNameAsString();
            if (isLogbackSpecificMethod(methodName)) {
                // Check if this is called on a Logger variable
                // This is a simplified check - real implementation would do more analysis
                System.out.println("Found logback-specific method call: " + methodName);
                modified = true;
            }
            return (Node) super.visit(n, arg);
        }
        
        private boolean isLogbackSpecificMethod(String methodName) {
            // List of logback-specific methods not in org.slf4j.Logger interface
            return "setLevel".equals(methodName) || 
                   "addAppender".equals(methodName) || 
                   "setAdditive".equals(methodName) || 
                   "detachAppender".equals(methodName) ||
                   "detachAndStopAllAppenders".equals(methodName) ||
                   "getAppender".equals(methodName) ||
                   "getEffectiveLevel".equals(methodName) ||
                   "getLevel".equals(methodName) ||
                   "getLoggerContext".equals(methodName) ||
                   "isAdditive".equals(methodName) ||
                   "isAttached".equals(methodName) ||
                   "iteratorForAppenders".equals(methodName) ||
                   "callAppenders".equals(methodName);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}