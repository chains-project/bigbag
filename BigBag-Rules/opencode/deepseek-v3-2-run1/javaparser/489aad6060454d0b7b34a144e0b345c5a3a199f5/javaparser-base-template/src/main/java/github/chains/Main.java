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

/**
 * Generic transformation rule for Flyway API breaking change (9.x to 10.x).
 * 
 * Transforms old pattern:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(dataSource);
 *   flyway.setClassLoader(classLoader);
 *   flyway.setLocations(locations);
 *   flyway.setValidateOnMigrate(validate);
 * 
 * To new pattern:
 *   Flyway flyway = Flyway.configure()  // or Flyway.configure(classLoader)
 *       .dataSource(dataSource)
 *       .locations(locations)
 *       .validateOnMigrate(validate)
 *       .load();
 * 
 * Also handles method chains: new Flyway().setDataSource(...).setLocations(...)
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("  Applies Flyway API migration transformation to all Java files in directory");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Applying Flyway API migration to: " + sourceDir);

        try {
            processDirectory(new File(sourceDir));
            System.out.println("Transformation complete");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processDirectory(File dir) throws Exception {
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Directory does not exist: " + dir.getAbsolutePath());
        }

        List<File> javaFiles = new ArrayList<>();
        collectJavaFiles(dir, javaFiles);

        System.out.println("Found " + javaFiles.size() + " Java files");

        int totalModified = 0;
        for (File javaFile : javaFiles) {
            int modified = processFile(javaFile);
            totalModified += modified;
        }
        
        System.out.println("Modified " + totalModified + " files total");
    }

    private static void collectJavaFiles(File dir, List<File> javaFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectJavaFiles(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }

    private static int processFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();

        FlywayTransformer visitor = new FlywayTransformer();
        cu.accept(visitor, null);

        if (visitor.getModifiedCount() > 0) {
            // Write back the modified file
            cu.setStorage(Paths.get(javaFile.getAbsolutePath()));
            try {
                cu.getStorage().ifPresent(s -> s.save());
                System.out.println("  Modified " + visitor.getModifiedCount() + " Flyway usage(s) in " + javaFile.getName());
            } catch (Exception e) {
                System.err.println("  Error saving " + javaFile.getName() + ": " + e.getMessage());
            }
            return 1;
        }
        return 0;
    }

    /**
     * Visitor that transforms Flyway API usage from old pattern to new pattern.
     */
    static class FlywayTransformer extends ModifierVisitor<Void> {
        private int modifiedCount = 0;

        public int getModifiedCount() {
            return modifiedCount;
        }

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Flyway constructor call
            if (isFlywayConstructor(n)) {
                // Get parent to understand context
                Node parent = n.getParentNode().orElse(null);
                
                if (parent instanceof VariableDeclarator) {
                    // Case 1: Flyway flyway = new Flyway();
                    return transformVariableDeclaration(n, (VariableDeclarator) parent);
                } else if (parent instanceof MethodCallExpr) {
                    // Case 2: new Flyway().setDataSource(...)
                    return transformMethodChain(n, (MethodCallExpr) parent);
                } else if (parent instanceof ReturnStmt) {
                    // Case 3: return new Flyway();
                    return transformReturnStatement(n, (ReturnStmt) parent);
                }
            }
            
            return super.visit(n, arg);
        }

        private boolean isFlywayConstructor(ObjectCreationExpr expr) {
            String typeName = expr.getType().asString();
            return "Flyway".equals(typeName) || "org.flywaydb.core.Flyway".equals(typeName);
        }

        private Expression transformVariableDeclaration(ObjectCreationExpr constructor, VariableDeclarator declarator) {
            String variableName = declarator.getNameAsString();
            
            // Find the containing block
            BlockStmt block = findEnclosingBlock(declarator);
            if (block == null) {
                return constructor;
            }
            
            // Find the statement containing this declarator
            int declIndex = -1;
            for (int i = 0; i < block.getStatements().size(); i++) {
                if (containsNode(block.getStatement(i), declarator)) {
                    declIndex = i;
                    break;
                }
            }
            
            if (declIndex == -1) {
                return constructor;
            }
            
            // Collect setter calls that follow this declaration
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
                if (isSetterCallOnVariable(call, variableName)) {
                    setters.add(call);
                } else {
                    break;
                }
            }
            
            if (setters.isEmpty()) {
                // Just new Flyway() with no configuration
                modifiedCount++;
                return createBasicConfigureCall();
            } else {
                // Build fluent API call
                MethodCallExpr fluentCall = buildFluentApiCall(setters);
                
                // Remove the setter statements
                for (MethodCallExpr setter : setters) {
                    Statement stmt = setter.findAncestor(Statement.class).orElse(null);
                    if (stmt != null) {
                        block.remove(stmt);
                    }
                }
                
                modifiedCount++;
                return fluentCall;
            }
        }

        private Expression transformMethodChain(ObjectCreationExpr constructor, MethodCallExpr methodCall) {
            // Walk up the method call chain
            List<MethodCallExpr> chain = new ArrayList<>();
            MethodCallExpr current = methodCall;
            
            while (current != null) {
                chain.add(current);
                Expression scope = current.getScope().orElse(null);
                if (scope instanceof MethodCallExpr) {
                    current = (MethodCallExpr) scope;
                } else if (scope instanceof ObjectCreationExpr && isFlywayConstructor((ObjectCreationExpr) scope)) {
                    // Found the constructor
                    break;
                } else {
                    // Unexpected structure
                    return constructor;
                }
            }
            
            // Reverse: constructor -> setter1 -> setter2 -> ...
            Collections.reverse(chain);
            
            // All except first are setter calls
            List<MethodCallExpr> setters = chain.subList(1, chain.size());
            
            // Build fluent API call
            modifiedCount++;
            return buildFluentApiCall(setters);
        }

        private Expression transformReturnStatement(ObjectCreationExpr constructor, ReturnStmt returnStmt) {
            Expression returnExpr = returnStmt.getExpression().orElse(null);
            
            if (returnExpr instanceof MethodCallExpr) {
                // It's return new Flyway().setDataSource(...)
                return transformMethodChain(constructor, (MethodCallExpr) returnExpr);
            } else {
                // Just return new Flyway();
                modifiedCount++;
                returnStmt.setExpression(createBasicConfigureCall());
                return constructor;
            }
        }

        private MethodCallExpr buildFluentApiCall(List<MethodCallExpr> setterCalls) {
            // Check for setClassLoader to determine configure() signature
            Expression classLoaderArg = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setterCalls) {
                if (setter.getNameAsString().equals("setClassLoader") && setter.getArguments().size() == 1) {
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
            
            // Chain all other setter calls as fluent method calls
            MethodCallExpr current = configureCall;
            for (MethodCallExpr setter : otherSetters) {
                String methodName = convertSetterToFluentMethod(setter.getNameAsString());
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

        private String convertSetterToFluentMethod(String setterName) {
            // Convert setXxx to xxx (camelCase)
            if (setterName.startsWith("set") && setterName.length() > 3) {
                String suffix = setterName.substring(3);
                return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
            }
            return setterName;
        }

        private boolean isSetterCallOnVariable(MethodCallExpr call, String variableName) {
            Expression scope = call.getScope().orElse(null);
            if (scope instanceof NameExpr) {
                String scopeName = ((NameExpr) scope).getNameAsString();
                if (scopeName.equals(variableName)) {
                    String methodName = call.getNameAsString();
                    // Check if it's a known Flyway setter method
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
            }
            return false;
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