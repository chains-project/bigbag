package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.ast.stmt.Statement;
import java.util.Collections;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
        
        JavaParser javaParser = new JavaParser();
        
        Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> {
                try {
                    CompilationUnit cu = javaParser.parse(p).getResult().orElseThrow();
                    new LogbackLoggerCastFixer().visit(cu, null);
                    
                    // Save changes
                    PrinterConfiguration config = new DefaultPrinterConfiguration();
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
                    String newContent = printer.print(cu);
                    Files.write(p, newContent.getBytes());
                    System.out.println("Processed: " + p);
                } catch (Exception e) {
                    System.err.println("Error processing " + p + ": " + e.getMessage());
                }
            });
        
        System.out.println("Transformation complete!");
    }
    
    static class LogbackLoggerCastFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(CastExpr castExpr, Void arg) {
            super.visit(castExpr, arg);
            
            // Check if this is a cast to ch.qos.logback.classic.Logger
            String castType = castExpr.getType().toString();
            if (castType.equals("Logger") || castType.equals("ch.qos.logback.classic.Logger")) {
                // Check if the expression being cast is a LoggerFactory.getLogger() call
                if (castExpr.getExpression() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) castExpr.getExpression();
                    if (methodCall.getNameAsString().equals("getLogger")) {
                        // Check if it's calling LoggerFactory.getLogger()
                        if (methodCall.getScope().isPresent()) {
                            String scope = methodCall.getScope().get().toString();
                            if (scope.equals("LoggerFactory") || scope.equals("org.slf4j.LoggerFactory")) {
                                // Replace Logger.ROOT_LOGGER_NAME with "ROOT" if present
                                replaceRootLoggerName(methodCall);
                                // Comment out the entire line to avoid LoggingEventAware dependency
                                commentOutSetLevelCall(castExpr);
                            }
                        }
                    }
                }
            }
        }
        
        private void commentOutSetLevelCall(CastExpr castExpr) {
            // Get the parent expression (should be a MethodCallExpr for setLevel)
            if (castExpr.getParentNode().isPresent() && castExpr.getParentNode().get() instanceof MethodCallExpr) {
                MethodCallExpr parentCall = (MethodCallExpr) castExpr.getParentNode().get();
                if (parentCall.getNameAsString().equals("setLevel")) {
                    // Remove the entire expression statement
                    if (parentCall.getParentNode().isPresent() && parentCall.getParentNode().get() instanceof ExpressionStmt) {
                        ExpressionStmt stmt = (ExpressionStmt) parentCall.getParentNode().get();
                        // Just remove the statement
                        stmt.remove();
                    }
                }
            }
        }
        
        private void replaceRootLoggerName(MethodCallExpr methodCall) {
            // Check if the argument is Logger.ROOT_LOGGER_NAME
            if (!methodCall.getArguments().isEmpty()) {
                var argExpr = methodCall.getArgument(0);
                if (argExpr instanceof FieldAccessExpr) {
                    FieldAccessExpr fieldAccess = (FieldAccessExpr) argExpr;
                    if (fieldAccess.getNameAsString().equals("ROOT_LOGGER_NAME") &&
                        (fieldAccess.getScope().toString().equals("Logger") || 
                         fieldAccess.getScope().toString().equals("ch.qos.logback.classic.Logger"))) {
                        // Replace Logger.ROOT_LOGGER_NAME with "ROOT"
                        methodCall.setArgument(0, new NameExpr("\"ROOT\""));
                    }
                }
            }
        }
    }
}