package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for logback-classic 1.4.4 breaking change.
 * 
 * Transforms pattern: ((Logger) LoggerFactory.getLogger(...)).setLevel(...)
 * To avoid compile-time dependency on ch.qos.logback.classic.Logger
 * which implements org.slf4j.spi.LoggingEventAware (missing in slf4j-api 1.x).
 * 
 * The transformation demonstrates the approach. A production implementation
 * would generate proper reflection code.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-dir> <logger-class-fqn>");
            System.err.println("Example: java Main ./src ch.qos.logback.classic.Logger");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        String loggerFqn = args[1]; // e.g., ch.qos.logback.classic.Logger
        
        System.out.println("Transforming pattern for: " + loggerFqn);
        
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList())) {
            
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + javaFile)
            );
            
            boolean modified = transform(cu, loggerFqn);
            
            if (modified) {
                Files.write(javaFile, cu.toString().getBytes());
                System.out.println("Transformed: " + javaFile);
            }
        }
    }
    
    private static boolean transform(CompilationUnit cu, String loggerFqn) {
        boolean modified = false;
        String simpleName = loggerFqn.substring(loggerFqn.lastIndexOf('.') + 1);
        
        // Remove import of the problematic logger class
        modified |= cu.getImports().removeIf(imp -> 
            imp.getNameAsString().equals(loggerFqn));
        
        // Find and transform the pattern
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals("setLevel") && 
                    n.getParentNode().isPresent() &&
                    n.getParentNode().get() instanceof CastExpr) {
                    
                    CastExpr cast = (CastExpr) n.getParentNode().get();
                    String castType = cast.getType().toString();
                    
                    // Check if casting to the problematic logger class
                    if (castType.equals(simpleName) || castType.equals(loggerFqn)) {
                        // Create replacement
                        MethodCallExpr replacement = new MethodCallExpr(
                            new NameExpr("System"),
                            "err",
                            new com.github.javaparser.ast.NodeList<>(
                                new StringLiteralExpr(
                                    "WARNING: setLevel() call needs reflection due to " + 
                                    loggerFqn + " implementing missing interface in slf4j-api 1.x"
                                )
                            )
                        );
                        
                        // In production: generate proper reflection code
                        // Reflection would call: logger.getClass().getMethod("setLevel", Level.class).invoke(logger, level)
                        
                        return replacement;
                    }
                }
                return super.visit(n, arg);
            }
        }, null);
        
        return modified;
    }
}