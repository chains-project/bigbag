package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        
        Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> processFile(p));
        
        System.out.println("Transformation completed.");
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new FileInputStream(filePath.toFile())).getResult().get();
            
            boolean modified = false;
            
            // Apply StringUtils.isAllBlank transformation
            IsAllBlankVisitor isAllBlankVisitor = new IsAllBlankVisitor();
            cu.accept(isAllBlankVisitor, null);
            modified = modified || isAllBlankVisitor.isModified();
            
            // Apply ClientHelper transformation
            ClientHelperVisitor clientHelperVisitor = new ClientHelperVisitor();
            cu.accept(clientHelperVisitor, null);
            modified = modified || clientHelperVisitor.isModified();
            
            // Remove static imports for isAllBlank and isNoneBlank
            StaticImportVisitor staticImportVisitor = new StaticImportVisitor();
            cu.accept(staticImportVisitor, null);
            modified = modified || staticImportVisitor.isModified();
            
            if (modified) {
                // Save the modified file
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String newContent = printer.print(cu);
                
                try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                    fos.write(newContent.getBytes());
                }
                
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    static class IsAllBlankVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a StringUtils.isAllBlank call
            if (n.getNameAsString().equals("isAllBlank")) {
                boolean isStringUtilsCall = false;
                
                // Check if it's StringUtils.isAllBlank() 
                if (n.getScope().isPresent() && n.getScope().get().toString().equals("StringUtils")) {
                    isStringUtilsCall = true;
                }
                // Check if it's a static import (no scope)
                else if (!n.getScope().isPresent()) {
                    // We'll assume it's StringUtils.isAllBlank if no scope
                    isStringUtilsCall = true;
                }
                
                if (isStringUtilsCall) {
                    // Get the arguments
                    List<Expression> args = n.getArguments();
                    if (!args.isEmpty()) {
                        // Create replacement: Arrays.stream(new CharSequence[]{...}).allMatch(StringUtils::isBlank)
                        String argsString = args.stream()
                            .map(Expression::toString)
                            .collect(Collectors.joining(", "));
                        
                        String replacement = String.format(
                            "java.util.Arrays.stream(new CharSequence[]{%s}).allMatch(org.apache.commons.lang3.StringUtils::isBlank)",
                            argsString
                        );
                        
                        try {
                            // Parse the replacement expression
                            JavaParser parser = new JavaParser();
                            Expression replacementExpr = parser.parseExpression(replacement).getResult().get();
                            
                            modified = true;
                            return replacementExpr;
                        } catch (Exception e) {
                            System.err.println("Failed to parse replacement for isAllBlank: " + e.getMessage());
                        }
                    }
                }
            }
            
            // Also handle isNoneBlank
            if (n.getNameAsString().equals("isNoneBlank")) {
                boolean isStringUtilsCall = false;
                
                // Check if it's StringUtils.isNoneBlank() 
                if (n.getScope().isPresent() && n.getScope().get().toString().equals("StringUtils")) {
                    isStringUtilsCall = true;
                }
                // Check if it's a static import (no scope)
                else if (!n.getScope().isPresent()) {
                    // We'll assume it's StringUtils.isNoneBlank if no scope
                    isStringUtilsCall = true;
                }
                
                if (isStringUtilsCall) {
                    // Get the arguments
                    List<Expression> args = n.getArguments();
                    if (!args.isEmpty()) {
                        // Create replacement: Arrays.stream(new CharSequence[]{...}).noneMatch(StringUtils::isBlank)
                        String argsString = args.stream()
                            .map(Expression::toString)
                            .collect(Collectors.joining(", "));
                        
                        String replacement = String.format(
                            "java.util.Arrays.stream(new CharSequence[]{%s}).noneMatch(org.apache.commons.lang3.StringUtils::isBlank)",
                            argsString
                        );
                        
                        try {
                            // Parse the replacement expression
                            JavaParser parser = new JavaParser();
                            Expression replacementExpr = parser.parseExpression(replacement).getResult().get();
                            
                            modified = true;
                            return replacementExpr;
                        } catch (Exception e) {
                            System.err.println("Failed to parse replacement for isNoneBlank: " + e.getMessage());
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    static class ClientHelperVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a ClientHelper constructor call
            if (n.getType().asString().equals("ClientHelper")) {
                List<Expression> args = n.getArguments();
                
                // Check for old signature: ClientHelper(String, TaskListener, String, String)
                if (args.size() == 4) {
                    String arg1 = args.get(0).toString();
                    String arg2 = args.get(1).toString();
                    String arg3 = args.get(2).toString();
                    String arg4 = args.get(3).toString();
                    
                    // Try to infer if this matches the old pattern
                    // The old pattern was: (credentials, listener, client, charset)
                    // We need to transform to: (item, credentials, listener, workspace)
                    
                    // Look for 'build' variable in context to get item
                    // build.getParent() would be the Item (Project)
                    // Create ManualWorkspaceImpl with client and charset
                    
                    String replacement = String.format(
                        "new org.jenkinsci.plugins.p4.client.ClientHelper(build.getParent(), %s, %s, " +
                        "new org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl(%s, false, %s, null))",
                        arg1, arg2, arg3, arg4
                    );
                    
                    try {
                        JavaParser parser = new JavaParser();
                        ObjectCreationExpr replacementExpr = (ObjectCreationExpr) 
                            parser.parseExpression(replacement).getResult().get();
                        
                        modified = true;
                        return replacementExpr;
                    } catch (Exception e) {
                        System.err.println("Failed to parse replacement: " + e.getMessage());
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    static class StaticImportVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            // Check if this is a static import of StringUtils.isAllBlank or StringUtils.isNoneBlank
            if (n.isStatic()) {
                String importName = n.getNameAsString();
                if (importName.equals("org.apache.commons.lang3.StringUtils.isAllBlank") || 
                    importName.equals("org.apache.commons.lang3.StringUtils.isNoneBlank")) {
                    // Remove this import
                    modified = true;
                    return null;
                }
            }
            
            return super.visit(n, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}