package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.NodeList;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        try {
            transformFlywayApi(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformFlywayApi(String sourceDir) throws IOException {
        Path start = Paths.get(sourceDir);
        List<Path> javaFiles = new ArrayList<>();
        
        Files.walk(start)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }
    
    private static void transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        FlywayApiTransformer transformer = new FlywayApiTransformer();
        cu.accept(transformer, null);
        
        Files.write(javaFile, cu.toString().getBytes());
    }
    
    private static class FlywayApiTransformer extends ModifierVisitor<Void> {
        private boolean inFlywayBeanMethod = false;
        private List<MethodCallExpr> flywayConfigCalls = new ArrayList<>();
        private ObjectCreationExpr flywayConstructor = null;
        
        @Override
        public Node visit(MethodDeclaration n, Void arg) {
            // Check if this method returns Flyway type
            if (n.getType().asString().equals("Flyway")) {
                inFlywayBeanMethod = true;
                flywayConfigCalls.clear();
                flywayConstructor = null;
                
                Node result = (Node) super.visit(n, arg);
                
                // After visiting the method body, apply transformation if needed
                if (flywayConstructor != null && !flywayConfigCalls.isEmpty()) {
                    return transformFlywayBeanMethod(n);
                }
                
                inFlywayBeanMethod = false;
                return result;
            }
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Flyway constructor call
            if (isFlywayConstructor(n) && inFlywayBeanMethod) {
                flywayConstructor = n;
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a Flyway configuration method call
            if (isFlywayConfigurationMethod(n) && inFlywayBeanMethod) {
                flywayConfigCalls.add(n);
            }
            return super.visit(n, arg);
        }
        
        private boolean isFlywayConstructor(ObjectCreationExpr expr) {
            return expr.getType().asString().equals("Flyway") && 
                   expr.getArguments().isEmpty();
        }
        
        private boolean isFlywayConfigurationMethod(MethodCallExpr expr) {
            String methodName = expr.getNameAsString();
            return (methodName.equals("setDataSource") || 
                    methodName.equals("setClassLoader") || 
                    methodName.equals("setLocations") || 
                    methodName.equals("setValidateOnMigrate")) &&
                   expr.getScope().isPresent() &&
                   expr.getScope().get() instanceof NameExpr &&
                   ((NameExpr) expr.getScope().get()).getNameAsString().equals("flyway");
        }
        
        private Node transformFlywayBeanMethod(MethodDeclaration method) {
            // Create the new fluent API pattern
            MethodCallExpr configureCall = new MethodCallExpr(
                new NameExpr("Flyway"), "configure"
            );
            
            // Apply all configuration calls to the fluent builder
            MethodCallExpr fluentBuilder = configureCall;
            for (MethodCallExpr configCall : flywayConfigCalls) {
                String methodName = configCall.getNameAsString();
                NodeList<Expression> arguments = configCall.getArguments();
                
                switch (methodName) {
                    case "setDataSource":
                        fluentBuilder = new MethodCallExpr(fluentBuilder, new SimpleName("dataSource"), arguments);
                        break;
                    case "setClassLoader":
                        fluentBuilder = new MethodCallExpr(fluentBuilder, new SimpleName("classLoader"), arguments);
                        break;
                    case "setLocations":
                        // Convert single string to varargs
                        if (arguments.size() == 1 && arguments.get(0) instanceof StringLiteralExpr) {
                            fluentBuilder = new MethodCallExpr(fluentBuilder, new SimpleName("locations"), arguments);
                        }
                        break;
                    case "setValidateOnMigrate":
                        fluentBuilder = new MethodCallExpr(fluentBuilder, new SimpleName("validateOnMigrate"), arguments);
                        break;
                }
            }
            
            // Final load() call
            MethodCallExpr loadCall = new MethodCallExpr(fluentBuilder, "load");
            
            // Replace the entire method body with the new pattern
            ExpressionStmt returnStmt = new ExpressionStmt(loadCall);
            method.setBody(new com.github.javaparser.ast.stmt.BlockStmt(
                new NodeList<>(returnStmt)
            ));
            
            return method;
        }
    }
}