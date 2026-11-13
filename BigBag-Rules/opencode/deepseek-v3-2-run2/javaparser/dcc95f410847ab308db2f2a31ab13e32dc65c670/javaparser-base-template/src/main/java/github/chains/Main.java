package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -cp . github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<File> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int filesModified = 0;
        int transformationsApplied = 0;
        
        for (File javaFile : javaFiles) {
            try {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                boolean modified = false;
                
                // Remove imports of ch.qos.logback.classic.Logger
                // Add import for org.slf4j.Logger if needed
                NodeList<ImportDeclaration> imports = cu.getImports();
                boolean hasSlf4jLogger = false;
                boolean hasLogbackLogger = false;
                
                for (int i = imports.size() - 1; i >= 0; i--) {
                    ImportDeclaration importDecl = imports.get(i);
                    String importName = importDecl.getNameAsString();
                    
                    if (importName.equals("ch.qos.logback.classic.Logger")) {
                        imports.remove(i);
                        modified = true;
                        hasLogbackLogger = true;
                        System.out.println("  Removed import: " + importName);
                    } else if (importName.equals("org.slf4j.Logger")) {
                        hasSlf4jLogger = true;
                    }
                }
                
                // Add org.slf4j.Logger import if we removed ch.qos.logback.classic.Logger
                if (hasLogbackLogger && !hasSlf4jLogger) {
                    imports.add(new ImportDeclaration("org.slf4j.Logger", false, false));
                    modified = true;
                    System.out.println("  Added import: org.slf4j.Logger");
                }
                
                // Apply transformation to fix Logger casts and logback-specific method calls
                LogbackTransformer transformer = new LogbackTransformer();
                cu.accept(transformer, null);
                
                if (transformer.transformationsApplied > 0) {
                    modified = true;
                    transformationsApplied += transformer.transformationsApplied;
                }
                
                if (modified) {
                    // Write the modified file back
                    Files.write(javaFile.toPath(), cu.toString().getBytes());
                    filesModified++;
                    System.out.println("Modified: " + javaFile + " (" + transformer.transformationsApplied + " transformations)");
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Transformations applied: " + transformationsApplied);
    }
    
    private static class LogbackTransformer extends ModifierVisitor<Void> {
        private int transformationsApplied = 0;
        
        @Override
        public Visitable visit(CastExpr castExpr, Void arg) {
            // Check if this is a cast to ch.qos.logback.classic.Logger
            String castType = castExpr.getType().asString();
            if (castType.equals("Logger") || castType.equals("ch.qos.logback.classic.Logger")) {
                // Remove the cast - just use the expression
                transformationsApplied++;
                System.out.println("  Removed cast to " + castType);
                return castExpr.getExpression();
            }
            return super.visit(castExpr, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Check for logback-specific method calls
            String methodName = methodCall.getNameAsString();
            
            // List of logback-specific methods that don't exist in SLF4J API
            List<String> logbackMethods = List.of("setLevel", "addAppender", "detachAppender", 
                                                  "getAppender", "isAttached", "iteratorForAppenders",
                                                  "detachAndStopAllAppenders", "getLevel", "getEffectiveLevel",
                                                  "setAdditive", "isAdditive", "getLoggerContext",
                                                  "callAppenders", "isEnabledFor");
            
            if (logbackMethods.contains(methodName)) {
                // We'll handle replacement at the statement level
                transformationsApplied++;
                System.out.println("  Found logback-specific method call: " + methodName);
            }
            return super.visit(methodCall, arg);
        }
        
        @Override
        public Visitable visit(ExpressionStmt stmt, Void arg) {
            // Check if this statement is a logback-specific method call
            if (stmt.getExpression().isMethodCallExpr()) {
                MethodCallExpr methodCall = stmt.getExpression().asMethodCallExpr();
                String methodName = methodCall.getNameAsString();
                
                List<String> logbackMethods = List.of("setLevel", "addAppender", "detachAppender", 
                                                      "getAppender", "isAttached", "iteratorForAppenders",
                                                      "detachAndStopAllAppenders", "getLevel", "getEffectiveLevel",
                                                      "setAdditive", "isAdditive", "getLoggerContext",
                                                      "callAppenders", "isEnabledFor");
                
                if (logbackMethods.contains(methodName)) {
                    // Remove the statement since it won't compile with SLF4J
                    transformationsApplied++;
                    return null;
                }
            }
            return super.visit(stmt, arg);
        }
    }
}