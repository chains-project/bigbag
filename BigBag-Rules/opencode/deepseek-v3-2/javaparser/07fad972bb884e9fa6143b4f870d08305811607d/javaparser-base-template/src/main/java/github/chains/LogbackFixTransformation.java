package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.util.Optional;

/**
 * Transformation to fix logback-classic 1.4.1 compatibility with SLF4J 1.x.
 * 
 * Problem: ch.qos.logback.classic.Logger implements org.slf4j.spi.LoggingEventAware
 * (SLF4J 2.x interface) which doesn't exist in SLF4J 1.x, causing compilation errors.
 * 
 * Solution: Replace casts to ch.qos.logback.classic.Logger and method calls
 * with reflective invocations.
 */
public class LogbackFixTransformation {
    
    public static CompilationUnit transform(CompilationUnit cu) {
        // Check if file needs transformation
        boolean needsTransformation = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("ch.qos.logback.classic.Logger")) {
                needsTransformation = true;
                break;
            }
        }
        
        if (!needsTransformation) {
            return cu;
        }
        
        // Apply transformation
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(CastExpr n, Void arg) {
                // Handle casts to Logger
                if (n.getType().isClassOrInterfaceType()) {
                    ClassOrInterfaceType type = n.getType().asClassOrInterfaceType();
                    if (type.getNameAsString().equals("Logger")) {
                        // Remove the cast - just return the expression
                        // The expression will be an org.slf4j.Logger which is compatible
                        return n.getExpression();
                    }
                }
                return super.visit(n, arg);
            }
            
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                // Handle method calls that might be on a Logger object
                String methodName = n.getNameAsString();
                
                if (methodName.equals("setLevel") || methodName.equals("addAppender")) {
                    Optional<Expression> scope = n.getScope();
                    if (scope.isPresent()) {
                        // Create reflective invocation
                        NodeList<Expression> args = n.getArguments();
                        
                        if (methodName.equals("setLevel") && args.size() == 1) {
                            // Replace with: try { scope.getClass().getMethod("setLevel", Class.forName("ch.qos.logback.classic.Level")).invoke(scope, arg); } catch (Exception e) { throw new RuntimeException(e); }
                            MethodCallExpr getClassCall = new MethodCallExpr(scope.get(), "getClass");
                            MethodCallExpr getMethodCall = new MethodCallExpr(getClassCall, "getMethod");
                            getMethodCall.addArgument(new NameExpr("\"setLevel\""));
                            
                            MethodCallExpr forNameCall = new MethodCallExpr(new NameExpr("Class"), "forName");
                            forNameCall.addArgument(new NameExpr("\"ch.qos.logback.classic.Level\""));
                            getMethodCall.addArgument(forNameCall);
                            
                            MethodCallExpr invokeCall = new MethodCallExpr(getMethodCall, "invoke");
                            invokeCall.addArgument(scope.get());
                            invokeCall.addArgument(args.get(0));
                            
                            // Wrap in try-catch (simplified - would need full try-catch block)
                            // For now, just return the invoke call
                            return invokeCall;
                        }
                    }
                }
                
                return super.visit(n, arg);
            }
        }, null);
        
        // Remove the problematic import
        cu.getImports().removeIf(importDecl -> 
            importDecl.getNameAsString().equals("ch.qos.logback.classic.Logger"));
        
        return cu;
    }
}