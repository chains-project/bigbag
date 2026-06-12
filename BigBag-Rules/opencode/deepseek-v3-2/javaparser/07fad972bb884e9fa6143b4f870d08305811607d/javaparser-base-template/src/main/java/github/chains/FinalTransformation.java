package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.util.Optional;

/**
 * Final transformation to fix logback 1.4.1 compatibility issue.
 * 
 * Transforms code like:
 *   Logger logger = (Logger) LoggerFactory.getLogger(SomeClass.class);
 *   logger.setLevel(Level.INFO);
 *   logger.addAppender(appender);
 * 
 * To:
 *   Object logger = LoggerFactory.getLogger(SomeClass.class);
 *   // Reflective setLevel
 *   try { logger.getClass().getMethod("setLevel", Class.forName("ch.qos.logback.classic.Level")).invoke(logger, Level.INFO); } catch (Exception e) { throw new RuntimeException(e); }
 *   // Reflective addAppender  
 *   try { logger.getClass().getMethod("addAppender", Class.forName("ch.qos.logback.core.Appender")).invoke(logger, appender); } catch (Exception e) { throw new RuntimeException(e); }
 */
public class FinalTransformation {
    
    public static CompilationUnit transform(CompilationUnit cu) {
        // Check if file imports ch.qos.logback.classic.Logger
        boolean hasImport = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("ch.qos.logback.classic.Logger")) {
                hasImport = true;
                break;
            }
        }
        
        if (!hasImport) {
            return cu;
        }
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(VariableDeclarationExpr n, Void arg) {
                // Change variable declarations of type Logger to Object
                if (n.getVariable(0).getType().isClassOrInterfaceType()) {
                    ClassOrInterfaceType type = n.getVariable(0).getType().asClassOrInterfaceType();
                    if (type.getNameAsString().equals("Logger")) {
                        type.setName("Object");
                    }
                }
                return super.visit(n, arg);
            }
            
            @Override
            public Visitable visit(CastExpr n, Void arg) {
                // Remove casts to Logger
                if (n.getType().isClassOrInterfaceType()) {
                    ClassOrInterfaceType type = n.getType().asClassOrInterfaceType();
                    if (type.getNameAsString().equals("Logger")) {
                        return n.getExpression();
                    }
                }
                return super.visit(n, arg);
            }
            
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                // Transform setLevel and addAppender calls
                String methodName = n.getNameAsString();
                if (methodName.equals("setLevel") || methodName.equals("addAppender")) {
                    Optional<Expression> scope = n.getScope();
                    if (scope.isPresent()) {
                        NodeList<Expression> args = n.getArguments();
                        
                        // Build reflective invocation
                        MethodCallExpr getClassCall = new MethodCallExpr(scope.get().clone(), "getClass");
                        MethodCallExpr getMethodCall = new MethodCallExpr(getClassCall, "getMethod");
                        getMethodCall.addArgument(new StringLiteralExpr(methodName));
                        
                        // Class.forName for the parameter type
                        String paramType = methodName.equals("setLevel") ? 
                            "ch.qos.logback.classic.Level" : "ch.qos.logback.core.Appender";
                        MethodCallExpr forNameCall = new MethodCallExpr(new NameExpr("Class"), "forName");
                        forNameCall.addArgument(new StringLiteralExpr(paramType));
                        getMethodCall.addArgument(forNameCall);
                        
                        // Invoke call
                        MethodCallExpr invokeCall = new MethodCallExpr(getMethodCall, "invoke");
                        invokeCall.addArgument(scope.get().clone());
                        for (Expression exprArg : args) {
                            invokeCall.addArgument(exprArg.clone());
                        }
                        
                        // Wrap in try-catch
                        BlockStmt tryBlock = new BlockStmt();
                        tryBlock.addStatement(new ExpressionStmt(invokeCall));
                        
                        // Catch clause
                        CatchClause catchClause = new CatchClause();
                        catchClause.setParameter(new com.github.javaparser.ast.body.Parameter(
                            new ClassOrInterfaceType("Exception"), "e"));
                        BlockStmt catchBlock = new BlockStmt();
                        MethodCallExpr throwExpr = new MethodCallExpr(new NameExpr("RuntimeException"), "RuntimeException");
                        throwExpr.addArgument(new NameExpr("e"));
                        ThrowStmt throwStmt = new ThrowStmt(throwExpr);
                        catchBlock.addStatement(throwStmt);
                        catchClause.setBody(catchBlock);
                        
                        TryStmt tryStmt = new TryStmt();
                        tryStmt.setTryBlock(tryBlock);
                        tryStmt.setCatchClauses(new NodeList<>(catchClause));
                        
                        return tryStmt;
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