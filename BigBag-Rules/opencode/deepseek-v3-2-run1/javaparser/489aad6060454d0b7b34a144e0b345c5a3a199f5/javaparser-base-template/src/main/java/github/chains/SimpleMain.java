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
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SimpleMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleMain <file>");
            System.exit(1);
        }
        
        File file = new File(args[0]);
        processFile(file);
    }
    
    private static void processFile(File javaFile) throws FileNotFoundException {
        System.out.println("Processing: " + javaFile.getAbsolutePath());
        
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        SimpleFlywayVisitor visitor = new SimpleFlywayVisitor();
        cu.accept(visitor, null);
        
        if (visitor.modified) {
            System.out.println("Modified file");
            // Write back
            cu.setStorage(Paths.get(javaFile.getAbsolutePath()));
            cu.getStorage().ifPresent(s -> {
                try {
                    s.save();
                } catch (Exception e) {
                    System.err.println("Error saving: " + e.getMessage());
                }
            });
        } else {
            System.out.println("No changes needed");
        }
    }
    
    static class SimpleFlywayVisitor extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if it's new Flyway()
            if (n.getType().asString().equals("Flyway") || 
                n.getType().asString().equals("org.flywaydb.core.Flyway")) {
                
                System.out.println("Found Flyway constructor");
                
                // Get parent
                Node parent = n.getParentNode().orElse(null);
                
                if (parent instanceof VariableDeclarator) {
                    // Flyway flyway = new Flyway();
                    return handleVariableDeclaration(n, (VariableDeclarator) parent);
                } else if (parent instanceof MethodCallExpr) {
                    // new Flyway().setDataSource(...)
                    return handleMethodChain(n, (MethodCallExpr) parent);
                }
            }
            
            return super.visit(n, arg);
        }
        
        private Expression handleVariableDeclaration(ObjectCreationExpr constructor, VariableDeclarator declarator) {
            System.out.println("  Variable declaration case");
            
            // Get variable name
            String varName = declarator.getNameAsString();
            
            // Find the block containing this
            BlockStmt block = findEnclosingBlock(declarator);
            if (block == null) {
                System.out.println("  No enclosing block found");
                return constructor;
            }
            
            // Find the statement with this declarator
            int declIndex = -1;
            for (int i = 0; i < block.getStatements().size(); i++) {
                if (containsNode(block.getStatement(i), declarator)) {
                    declIndex = i;
                    break;
                }
            }
            
            if (declIndex == -1) {
                System.out.println("  Could not find declaration statement");
                return constructor;
            }
            
            // Find subsequent setter calls
            List<MethodCallExpr> setters = new ArrayList<>();
            for (int i = declIndex + 1; i < block.getStatements().size(); i++) {
                Statement stmt = block.getStatement(i);
                if (!(stmt instanceof ExpressionStmt)) {
                    break;
                }
                
                Expression expr = ((ExpressionStmt) stmt).getExpression();
                if (!(expr instanceof MethodCallExpr)) {
                    break;
                }
                
                MethodCallExpr call = (MethodCallExpr) expr;
                if (isSetterOnVariable(call, varName)) {
                    setters.add(call);
                } else {
                    break;
                }
            }
            
            System.out.println("  Found " + setters.size() + " setter calls");
            
            if (setters.isEmpty()) {
                // Just new Flyway() with no setters
                modified = true;
                return createSimpleConfigure();
            } else {
                // Build fluent call
                MethodCallExpr fluentCall = buildFluentCall(setters);
                
                // Remove setter statements
                for (MethodCallExpr setter : setters) {
                    Statement stmt = setter.findAncestor(Statement.class).orElse(null);
                    if (stmt != null) {
                        block.remove(stmt);
                    }
                }
                
                modified = true;
                return fluentCall;
            }
        }
        
        private Visitable handleMethodChain(ObjectCreationExpr constructor, MethodCallExpr methodCall) {
            System.out.println("  Method chain case");
            
            // Walk up the chain
            List<MethodCallExpr> chain = new ArrayList<>();
            MethodCallExpr current = methodCall;
            
            while (current != null) {
                chain.add(current);
                Expression scope = current.getScope().orElse(null);
                if (scope instanceof MethodCallExpr) {
                    current = (MethodCallExpr) scope;
                } else if (scope instanceof ObjectCreationExpr) {
                    // Found constructor
                    break;
                } else {
                    // Unexpected
                    return constructor;
                }
            }
            
            // Reverse: constructor -> setter1 -> setter2 -> ...
            Collections.reverse(chain);
            
            // All except first are setters
            List<MethodCallExpr> setters = chain.subList(1, chain.size());
            
            System.out.println("  Chain has " + setters.size() + " setters");
            
            // Build and return fluent call
            MethodCallExpr fluentCall = buildFluentCall(setters);
            modified = true;
            return fluentCall;
        }
        
        private MethodCallExpr buildFluentCall(List<MethodCallExpr> setters) {
            // Check for setClassLoader
            Expression classLoaderArg = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setters) {
                if (setter.getNameAsString().equals("setClassLoader") && 
                    setter.getArguments().size() == 1) {
                    classLoaderArg = setter.getArgument(0);
                } else {
                    otherSetters.add(setter);
                }
            }
            
            // Start with configure()
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
            
            // Chain other setters
            MethodCallExpr current = configureCall;
            for (MethodCallExpr setter : otherSetters) {
                String methodName = convertSetterName(setter.getNameAsString());
                current = new MethodCallExpr(current, methodName, setter.getArguments());
            }
            
            // Add load()
            return new MethodCallExpr(current, "load", new NodeList<>());
        }
        
        private MethodCallExpr createSimpleConfigure() {
            return new MethodCallExpr(
                new MethodCallExpr(new NameExpr("Flyway"), "configure", new NodeList<>()),
                "load",
                new NodeList<>()
            );
        }
        
        private String convertSetterName(String setterName) {
            if (setterName.startsWith("set") && setterName.length() > 3) {
                return Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);
            }
            return setterName;
        }
        
        private boolean isSetterOnVariable(MethodCallExpr call, String varName) {
            Expression scope = call.getScope().orElse(null);
            if (!(scope instanceof NameExpr)) {
                return false;
            }
            
            String scopeName = ((NameExpr) scope).getNameAsString();
            if (!scopeName.equals(varName)) {
                return false;
            }
            
            String methodName = call.getNameAsString();
            return methodName.startsWith("set") && (
                methodName.equals("setDataSource") ||
                methodName.equals("setClassLoader") ||
                methodName.equals("setLocations") ||
                methodName.equals("setValidateOnMigrate") ||
                methodName.equals("setBaselineOnMigrate") ||
                methodName.equals("setBaselineVersion") ||
                methodName.equals("setBaselineDescription") ||
                methodName.equals("setPlaceholders") ||
                methodName.equals("setPlaceholderPrefix") ||
                methodName.equals("setPlaceholderSuffix") ||
                methodName.equals("setSqlMigrationPrefix") ||
                methodName.equals("setRepeatableSqlMigrationPrefix") ||
                methodName.equals("setTablespace") ||
                methodName.equals("setTarget") ||
                methodName.equals("setOutOfOrder") ||
                methodName.equals("setCleanOnValidationError") ||
                methodName.equals("setCleanDisabled")
            );
        }
        
        private BlockStmt findEnclosingBlock(Node node) {
            Node current = node;
            while (current != null) {
                if (current instanceof BlockStmt) {
                    return (BlockStmt) current;
                }
                current = current.getParentNode().orElse(null);
            }
            return null;
        }
        
        private boolean containsNode(Node container, Node target) {
            Node current = target;
            while (current != null) {
                if (current == container) {
                    return true;
                }
                current = current.getParentNode().orElse(null);
            }
            return false;
        }
    }
}