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
 * Generic transformation to fix logback 1.4.1 + SLF4J 1.x compatibility issue.
 * 
 * Breaking Change: ch.qos.logback.classic.Logger implements 
 * org.slf4j.spi.LoggingEventAware (SLF4J 2.x interface) which doesn't exist
 * in SLF4J 1.x, causing compilation errors.
 * 
 * Transformation Pattern:
 * Old: Logger logger = (Logger) LoggerFactory.getLogger(...);
 *      logger.setLevel(level);
 *      logger.addAppender(appender);
 * 
 * New: Object logger = LoggerFactory.getLogger(...);
 *      if (logger.getClass().getName().equals("ch.qos.logback.classic.Logger")) {
 *          try { reflective invocation } catch (Exception e) { ... }
 *      }
 */
public class LogbackCompatibilityFix {
    
    public static CompilationUnit transform(CompilationUnit cu) {
        // Check if file uses ch.qos.logback.classic.Logger
        boolean hasImport = cu.getImports().stream()
            .anyMatch(importDecl -> importDecl.getNameAsString().equals("ch.qos.logback.classic.Logger"));
        
        if (!hasImport) {
            return cu;
        }
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(VariableDeclarationExpr n, Void arg) {
                // Change 'Logger' type to 'Object'
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
                // Transform setLevel and addAppender method calls
                String methodName = n.getNameAsString();
                if (methodName.equals("setLevel") || methodName.equals("addAppender")) {
                    Optional<Expression> scope = n.getScope();
                    if (scope.isPresent() && n.getArguments().size() == 1) {
                        // Get the variable name from scope
                        String varName = scope.get().toString();
                        
                        // Create the reflective invocation block
                        BlockStmt reflectiveBlock = createReflectiveBlock(varName, methodName, n.getArgument(0));
                        
                        // Wrap in if statement checking if it's a logback Logger
                        MethodCallExpr getNameCall = new MethodCallExpr(
                            new MethodCallExpr(new NameExpr(varName), "getClass"), "getName");
                        BinaryExpr condition = new BinaryExpr(
                            getNameCall,
                            new StringLiteralExpr("ch.qos.logback.classic.Logger"),
                            BinaryExpr.Operator.EQUALS);
                        
                        BlockStmt ifBlock = new BlockStmt();
                        ifBlock.addStatement(reflectiveBlock);
                        
                        IfStmt ifStmt = new IfStmt(condition, ifBlock, null);
                        return ifStmt;
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
    
    private static BlockStmt createReflectiveBlock(String varName, String methodName, Expression arg) {
        // Build: try { varName.getClass().getMethod(methodName, Class.forName(type)).invoke(varName, arg); } catch (Exception e) { throw new RuntimeException(e); }
        
        String paramType = methodName.equals("setLevel") ? 
            "ch.qos.logback.classic.Level" : "ch.qos.logback.core.Appender";
        
        // try block
        BlockStmt tryBlock = new BlockStmt();
        
        // varName.getClass()
        MethodCallExpr getClassCall = new MethodCallExpr(new NameExpr(varName), "getClass");
        
        // .getMethod(methodName, Class.forName(type))
        MethodCallExpr getMethodCall = new MethodCallExpr(getClassCall, "getMethod");
        getMethodCall.addArgument(new StringLiteralExpr(methodName));
        
        MethodCallExpr forNameCall = new MethodCallExpr(new NameExpr("Class"), "forName");
        forNameCall.addArgument(new StringLiteralExpr(paramType));
        getMethodCall.addArgument(forNameCall);
        
        // .invoke(varName, arg)
        MethodCallExpr invokeCall = new MethodCallExpr(getMethodCall, "invoke");
        invokeCall.addArgument(new NameExpr(varName));
        invokeCall.addArgument(arg.clone());
        
        tryBlock.addStatement(new ExpressionStmt(invokeCall));
        
        // catch block
        CatchClause catchClause = new CatchClause();
        catchClause.setParameter(new com.github.javaparser.ast.body.Parameter(
            new ClassOrInterfaceType("Exception"), "e"));
        
        BlockStmt catchBlock = new BlockStmt();
        MethodCallExpr constructorCall = new MethodCallExpr(new NameExpr("RuntimeException"), "RuntimeException");
        constructorCall.addArgument(new NameExpr("e"));
        ThrowStmt throwStmt = new ThrowStmt(constructorCall);
        catchBlock.addStatement(throwStmt);
        catchClause.setBody(catchBlock);
        
        // try statement
        TryStmt tryStmt = new TryStmt();
        tryStmt.setTryBlock(tryBlock);
        tryStmt.setCatchClauses(new NodeList<>(catchClause));
        
        // Wrap in block
        BlockStmt block = new BlockStmt();
        block.addStatement(tryStmt);
        return block;
    }
}