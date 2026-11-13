package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    /**
     * Generic transformation rule for logback Appender.doAppend method calls.
     * 
     * Breaking Change Pattern:
     * - Old API: appender.doAppend(event)
     * - New API: appender.doAppend(event) throws AppenderException
     * 
     * This transformation wraps doAppend calls in try-catch blocks to handle
     * the new checked exception requirement in logback 1.4.0+
     */
    public static class LogbackAppenderTransformation extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Check if this statement contains a doAppend method call
            if (n.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) n.getExpression();
                if (isDoAppendCall(methodCall)) {
                    // Replace the expression statement with a try-catch block
                    return wrapInTryCatch(methodCall);
                }
            }
            return super.visit(n, arg);
        }
        
        private boolean isDoAppendCall(MethodCallExpr methodCall) {
            return "doAppend".equals(methodCall.getNameAsString());
        }
        
        private TryStmt wrapInTryCatch(MethodCallExpr methodCall) {
            // Create try block with the method call
            BlockStmt tryBlock = new BlockStmt();
            tryBlock.addStatement(new ExpressionStmt(methodCall));
            
            // Create catch clause for AppenderException by parsing a full try-catch statement
            String tryCatchCode = "try { dummy(); } catch (ch.qos.logback.core.spi.AppenderException e) { /* ignore */ } ";
            CompilationUnit cu = StaticJavaParser.parse("class Dummy { void method() { " + tryCatchCode + " } }");
            TryStmt tryStmt = (TryStmt) cu.getClassByName("Dummy").get()
                    .getMethods().get(0)
                    .getBody().get()
                    .getStatements().get(0);
            
            // Replace the dummy method call with our actual method call
            tryStmt.getTryBlock().getStatements().set(0, new ExpressionStmt(methodCall));
            
            return tryStmt;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying logback Appender transformation to: " + sourceDir);
        
        try {
            // Find all Java files in the source directory
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            int transformedFiles = 0;
            int transformedCalls = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    // Parse the Java file
                    CompilationUnit cu = StaticJavaParser.parse(javaFile);
                    
                    // Apply the transformation
                    LogbackAppenderTransformation transformation = new LogbackAppenderTransformation();
                    CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformation, null);
                    
                    // Check if any transformations were applied
                    if (!cu.equals(transformedCu)) {
                        // Write the transformed file back
                        String transformedCode = transformedCu.toString();
                        Files.write(javaFile, transformedCode.getBytes());
                        
                        transformedFiles++;
                        
                        // Count the number of transformed method calls
                        transformedCalls += countDoAppendCalls(cu);
                        
                        System.out.println("Transformed: " + javaFile);
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            }
            
            System.out.println("Transformation complete!");
            System.out.println("Files transformed: " + transformedFiles);
            System.out.println("doAppend calls handled: " + transformedCalls);
            
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static int countDoAppendCalls(CompilationUnit cu) {
        final int[] count = {0};
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                if ("doAppend".equals(n.getNameAsString())) {
                    count[0]++;
                }
                return super.visit(n, arg);
            }
        }, null);
        return count[0];
    }
}