package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SimpleFlywayTransformer {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleFlywayTransformer <file>");
            System.exit(1);
        }
        
        Path filePath = Paths.get(args[0]);
        String content = Files.readString(filePath);
        
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow();
        
        FlywayTransformer visitor = new FlywayTransformer();
        cu.accept(visitor, null);
        
        System.out.println("Modified: " + visitor.modifiedCount);
        System.out.println(cu.toString());
    }
    
    static class FlywayTransformer extends ModifierVisitor<Void> {
        int modifiedCount = 0;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if it's a Flyway constructor
            if (n.getType().asString().equals("Flyway") || 
                n.getType().asString().equals("org.flywaydb.core.Flyway")) {
                
                System.out.println("Found Flyway constructor");
                
                // Check parent context to understand how it's being used
                Node parent = n.getParentNode().orElse(null);
                
                if (parent instanceof VariableDeclarator) {
                    // Case: Flyway flyway = new Flyway();
                    return handleVariableDeclarationCase(n, (VariableDeclarator) parent);
                } else if (parent instanceof MethodCallExpr) {
                    // Case: new Flyway().setDataSource(...)
                    return handleMethodChainCase(n, (MethodCallExpr) parent);
                } else if (parent instanceof ReturnStmt) {
                    // Case: return new Flyway();
                    return handleReturnCase(n, (ReturnStmt) parent);
                }
            }
            
            return super.visit(n, arg);
        }
        
        private Visitable handleVariableDeclarationCase(ObjectCreationExpr constructor, VariableDeclarator declarator) {
            System.out.println("  Variable declaration case");
            
            // Find the block containing this declaration
            Node block = findEnclosingBlock(declarator);
            if (!(block instanceof BlockStmt)) {
                return constructor;
            }
            
            BlockStmt blockStmt = (BlockStmt) block;
            String varName = declarator.getNameAsString();
            
            // Find all statements in the block
            List<Statement> statements = blockStmt.getStatements();
            
            // Find the index of the declaration statement
            int declIndex = -1;
            for (int i = 0; i < statements.size(); i++) {
                if (statements.get(i).toString().contains("Flyway " + varName + " =")) {
                    declIndex = i;
                    break;
                }
            }
            
            if (declIndex == -1) {
                return constructor;
            }
            
            // Collect setter calls that follow
            List<MethodCallExpr> setters = new ArrayList<>();
            for (int i = declIndex + 1; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof MethodCallExpr) {
                        MethodCallExpr call = (MethodCallExpr) expr;
                        if (isSetterCallOnVariable(call, varName)) {
                            setters.add(call);
                        } else {
                            // Stop at first non-setter
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            
            if (setters.isEmpty()) {
                System.out.println("  No setters found after declaration");
                // Still need to transform constructor to Flyway.configure().load()
                declarator.setInitializer(createBasicConfigureCall());
                modifiedCount++;
                return constructor;
            }
            
            System.out.println("  Found " + setters.size() + " setters");
            
            // Build new fluent API call
            MethodCallExpr newCall = buildFluentCall(setters);
            
            // Replace the initializer
            declarator.setInitializer(newCall);
            
            // Remove the setter statements
            for (MethodCallExpr setter : setters) {
                Statement stmt = setter.findAncestor(Statement.class).orElse(null);
                if (stmt != null) {
                    blockStmt.remove(stmt);
                }
            }
            
            modifiedCount++;
            return constructor;
        }
        
        private Visitable handleMethodChainCase(ObjectCreationExpr constructor, MethodCallExpr parentCall) {
            System.out.println("  Method chain case");
            
            // Find the root of the chain
            List<MethodCallExpr> chain = new ArrayList<>();
            MethodCallExpr current = parentCall;
            while (current != null) {
                chain.add(current);
                Expression scope = current.getScope().orElse(null);
                if (scope instanceof MethodCallExpr) {
                    current = (MethodCallExpr) scope;
                } else if (scope instanceof ObjectCreationExpr) {
                    // Found the constructor
                    break;
                } else {
                    // Unexpected
                    return constructor;
                }
            }
            
            // Reverse to get constructor -> setter1 -> setter2 -> ...
            Collections.reverse(chain);
            
            // The first element is the call immediately after constructor
            // We need to build a new fluent call
            List<MethodCallExpr> setters = chain.subList(1, chain.size());
            MethodCallExpr newCall = buildFluentCall(setters);
            
            modifiedCount++;
            return newCall;
        }
        
        private Visitable handleReturnCase(ObjectCreationExpr constructor, ReturnStmt returnStmt) {
            System.out.println("  Return case");
            
            // Check if return statement has method calls chained
            Expression returnExpr = returnStmt.getExpression().orElse(null);
            if (returnExpr instanceof MethodCallExpr) {
                // It's return new Flyway().setDataSource(...)
                return handleMethodChainCase(constructor, (MethodCallExpr) returnExpr);
            } else {
                // Just return new Flyway();
                returnStmt.setExpression(createBasicConfigureCall());
                modifiedCount++;
                return constructor;
            }
        }
        
        private MethodCallExpr buildFluentCall(List<MethodCallExpr> setters) {
            System.out.println("  Building fluent call with " + setters.size() + " setters");
            
            // Check for setClassLoader to determine which configure() to use
            Expression classLoaderArg = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setters) {
                if (setter.getNameAsString().equals("setClassLoader")) {
                    classLoaderArg = setter.getArgument(0);
                } else {
                    otherSetters.add(setter);
                }
            }
            
            // Start with Flyway.configure() or Flyway.configure(classLoader)
            MethodCallExpr configureCall;
            if (classLoaderArg != null) {
                configureCall = new MethodCallExpr(
                    new NameExpr("Flyway"),
                    "configure",
                    new NodeList<>(classLoaderArg)
                );
            } else {
                configureCall = new MethodCallExpr(
                    new NameExpr("Flyway"),
                    "configure",
                    new NodeList<>()
                );
            }
            
            // Chain all other setters
            MethodCallExpr current = configureCall;
            for (MethodCallExpr setter : otherSetters) {
                String methodName = convertSetterName(setter.getNameAsString());
                current = new MethodCallExpr(current, methodName, setter.getArguments());
            }
            
            // Add .load() at the end
            return new MethodCallExpr(current, "load", new NodeList<>());
        }
        
        private MethodCallExpr createBasicConfigureCall() {
            return new MethodCallExpr(
                new MethodCallExpr(new NameExpr("Flyway"), "configure", new NodeList<>()),
                "load",
                new NodeList<>()
            );
        }
        
        private String convertSetterName(String setterName) {
            // Convert setXxx to xxx (camelCase)
            if (setterName.startsWith("set") && setterName.length() > 3) {
                return Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);
            }
            return setterName;
        }
        
        private boolean isSetterCallOnVariable(MethodCallExpr call, String varName) {
            Expression scope = call.getScope().orElse(null);
            if (scope instanceof NameExpr) {
                return ((NameExpr) scope).getNameAsString().equals(varName);
            }
            return false;
        }
        
        private Node findEnclosingBlock(Node node) {
            Node current = node;
            while (current != null) {
                if (current instanceof BlockStmt) {
                    return current;
                }
                current = current.getParentNode().orElse(null);
            }
            return null;
        }
    }
}