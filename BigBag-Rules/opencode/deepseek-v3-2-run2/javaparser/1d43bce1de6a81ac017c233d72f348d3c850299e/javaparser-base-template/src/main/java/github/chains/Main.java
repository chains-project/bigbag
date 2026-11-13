package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            System.out.println("Processing file: " + filePath);
            
            FlywayMigrationTransformer transformer = new FlywayMigrationTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                String transformedContent = cu.toString();
                System.out.println("Writing transformed content:\n" + transformedContent);
                System.out.println("Original file exists: " + Files.exists(filePath));
                System.out.println("File is writable: " + Files.isWritable(filePath));
                Files.writeString(filePath, transformedContent);
                System.out.println("Transformed: " + filePath);
                System.out.println("File size after write: " + Files.size(filePath));
            } else {
                System.out.println("No transformations applied to: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class FlywayMigrationTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Flyway constructor call
            if (n.getType().asString().equals("Flyway") && n.getArguments().isEmpty()) {
                System.out.println("Found Flyway constructor");
                // Find the parent variable declarator
                Optional<Node> parent = n.getParentNode();
                if (parent.isPresent() && parent.get() instanceof VariableDeclarator) {
                    VariableDeclarator varDeclarator = (VariableDeclarator) parent.get();
                    
                    // Transform to new API pattern - simple case with no method calls
                    ObjectCreationExpr newConstructor = createSimpleNewFlywayConstructor();
                    System.out.println("Setting initializer to: " + newConstructor.toString());
                    System.out.println("Original initializer: " + varDeclarator.getInitializer().get().toString());
                    varDeclarator.setInitializer(newConstructor);
                    System.out.println("New initializer: " + varDeclarator.getInitializer().get().toString());
                    
                    modified = true;
                    System.out.println("Transformation completed successfully");
                } else {
                    System.out.println("Parent is not VariableDeclarator: " + (parent.isPresent() ? parent.get().getClass().getSimpleName() : "empty"));
                }
            }
            
            return super.visit(n, arg);
        }
        
        private List<MethodCallExpr> collectFlywayMethodCalls(BlockStmt block, int startIndex, VariableDeclarationExpr varDecl) {
            List<MethodCallExpr> flywayCalls = new ArrayList<>();
            String varName = varDecl.getVariables().get(0).getNameAsString();
            
            System.out.println("Looking for method calls on variable: " + varName);
            
            for (int i = startIndex; i < block.getStatements().size(); i++) {
                if (block.getStatements().get(i) instanceof ExpressionStmt) {
                    ExpressionStmt stmt = (ExpressionStmt) block.getStatements().get(i);
                    if (stmt.getExpression() instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) stmt.getExpression();
                        
                        // Check if this is a method call on our Flyway variable
                        if (methodCall.getScope().isPresent() && 
                            methodCall.getScope().get() instanceof NameExpr &&
                            ((NameExpr) methodCall.getScope().get()).getNameAsString().equals(varName)) {
                            
                            String methodName = methodCall.getNameAsString();
                            System.out.println("Found method call: " + methodName);
                            if (isFlywayConfigurationMethod(methodName)) {
                                flywayCalls.add(methodCall);
                            } else {
                                // Stop at first non-configuration method
                                break;
                            }
                        } else {
                            // Stop at first non-Flyway method call
                            break;
                        }
                    } else {
                        // Stop at first non-method-call statement
                        break;
                    }
                } else {
                    // Stop at first non-expression statement
                    break;
                }
            }
            
            return flywayCalls;
        }
        
        private boolean isFlywayConfigurationMethod(String methodName) {
            return methodName.equals("setDataSource") ||
                   methodName.equals("setClassLoader") ||
                   methodName.equals("setLocations") ||
                   methodName.equals("setValidateOnMigrate");
        }
        
        private ObjectCreationExpr createNewFlywayConstructor(List<MethodCallExpr> flywayCalls) {
            System.out.println("Creating new constructor with " + flywayCalls.size() + " method calls");
            // Create FluentConfiguration.configure() call
            MethodCallExpr configureCall = new MethodCallExpr(
                new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"),
                "configure"
            );
            
            // Chain all configuration methods
            MethodCallExpr currentCall = configureCall;
            for (MethodCallExpr methodCall : flywayCalls) {
                String newMethodName = mapOldMethodToNew(methodCall.getNameAsString());
                System.out.println("Adding method: " + newMethodName);
                currentCall = new MethodCallExpr(currentCall, newMethodName);
                
                // Copy arguments
                for (Expression arg : methodCall.getArguments()) {
                    currentCall.addArgument(arg.clone());
                }
            }
            
            // Add .load() at the end
            MethodCallExpr loadCall = new MethodCallExpr(currentCall, "load");
            
            // Create new Flyway constructor with configuration
            ObjectCreationExpr newConstructor = new ObjectCreationExpr();
            newConstructor.setType("org.flywaydb.core.Flyway");
            newConstructor.addArgument(loadCall);
            
            System.out.println("Created constructor: " + newConstructor.toString());
            return newConstructor;
        }
        
        private String mapOldMethodToNew(String oldMethodName) {
            switch (oldMethodName) {
                case "setDataSource": return "dataSource";
                case "setClassLoader": return "classLoader";
                case "setLocations": return "locations";
                case "setValidateOnMigrate": return "validateOnMigrate";
                default: return oldMethodName;
            }
        }
        
        private void removeFlywayMethodCalls(BlockStmt block, List<MethodCallExpr> flywayCalls) {
            for (MethodCallExpr methodCall : flywayCalls) {
                Optional<Node> parent = methodCall.getParentNode();
                if (parent.isPresent() && parent.get() instanceof ExpressionStmt) {
                    ExpressionStmt stmt = (ExpressionStmt) parent.get();
                    block.getStatements().remove(stmt);
                }
            }
        }
        
        private ObjectCreationExpr createSimpleNewFlywayConstructor() {
            // Create FluentConfiguration.configure() call
            MethodCallExpr configureCall = new MethodCallExpr(
                new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"),
                "configure"
            );
            
            // Add .load() at the end
            MethodCallExpr loadCall = new MethodCallExpr(configureCall, "load");
            
            // Create new Flyway constructor with configuration
            ObjectCreationExpr newConstructor = new ObjectCreationExpr();
            newConstructor.setType("org.flywaydb.core.Flyway");
            newConstructor.addArgument(loadCall);
            
            System.out.println("Created simple constructor: " + newConstructor.toString());
            return newConstructor;
        }
    }
}