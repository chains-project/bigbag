package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<File> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        int transformedFiles = 0;
        for (File javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformation complete. Modified " + transformedFiles + " files.");
    }
    
    private static boolean transformFile(File javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream(javaFile)).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        FlywayTransformer transformer = new FlywayTransformer();
        CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformer, null);
        
        if (transformer.wasModified()) {
            try (FileWriter writer = new FileWriter(javaFile)) {
                writer.write(transformedCu.toString());
            }
            System.out.println("Transformed: " + javaFile.getPath());
            return true;
        }
        
        return false;
    }
    
    static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        private Map<String, List<MethodCallExpr>> variableMethodCalls = new HashMap<>();
        private Set<Node> nodesToRemove = new HashSet<>();
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a new Flyway() constructor call
            if (n.getType().toString().equals("Flyway") || 
                n.getType().toString().equals("org.flywaydb.core.Flyway")) {
                
                // Check if there are no arguments (old API)
                if (n.getArguments().isEmpty()) {
                    modified = true;
                    
                    // Check if parent is variable declaration
                    Optional<Node> parent = n.getParentNode();
                    if (parent.isPresent() && parent.get() instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) parent.get();
                        String varName = varDecl.getVariable(0).getNameAsString();
                        
                        // Collect method calls on this variable
                        List<MethodCallExpr> methodCalls = collectMethodCallsForVariable(varName, n);
                        
                        // Build the new fluent configuration
                        StringBuilder newCode = new StringBuilder();
                        if (n.getType().toString().equals("org.flywaydb.core.Flyway")) {
                            newCode.append("org.flywaydb.core.Flyway.configure()");
                        } else {
                            newCode.append("Flyway.configure()");
                        }
                        
                        // Transform setter methods to fluent configuration methods
                        for (MethodCallExpr mc : methodCalls) {
                            String methodName = mc.getNameAsString();
                            String fluentMethodName = mapSetterToFluentMethod(methodName);
                            
                            if (fluentMethodName != null) {
                                newCode.append(".").append(fluentMethodName).append("(");
                                
                                // Add arguments
                                List<String> args = mc.getArguments().stream()
                                    .map(Node::toString)
                                    .collect(Collectors.toList());
                                newCode.append(String.join(", ", args));
                                
                                newCode.append(")");
                            }
                            
                            // Mark method call for removal
                            nodesToRemove.add(mc);
                        }
                        
                        newCode.append(".load()");
                        
                        // Check if we should replace the entire return statement
                        Optional<ReturnStmt> returnStmt = varDecl.findAncestor(ReturnStmt.class);
                        if (returnStmt.isPresent()) {
                            // Replace return statement with fluent chain
                            ReturnStmt rs = returnStmt.get();
                            if (rs.getExpression().isPresent() && 
                                rs.getExpression().get().toString().equals(varName)) {
                                // This variable is being returned, replace entire return
                                nodesToRemove.add(varDecl.findAncestor(ExpressionStmt.class).orElse(null));
                                JavaParser parser = new JavaParser();
                                return parser.parseExpression(newCode.toString()).getResult().orElse(n);
                            }
                        }
                        
                        // Parse and return the new expression
                        JavaParser parser = new JavaParser();
                        return parser.parseExpression(newCode.toString()).getResult().orElse(n);
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Skip method calls marked for removal
            if (nodesToRemove.contains(n)) {
                return null;
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Check if this statement contains a method call marked for removal
            if (n.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr mc = (MethodCallExpr) n.getExpression();
                if (nodesToRemove.contains(mc)) {
                    return null;
                }
            }
            // Check if this is a variable declaration that we transformed
            if (n.getExpression() instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) n.getExpression();
                if (nodesToRemove.contains(varDecl)) {
                    return null;
                }
            }
            return super.visit(n, arg);
        }
        
        private List<MethodCallExpr> collectMethodCallsForVariable(String varName, ObjectCreationExpr creationExpr) {
            List<MethodCallExpr> methodCalls = new ArrayList<>();
            
            // Find the block containing the creation expression
            Optional<Node> block = creationExpr.findAncestor(Node.class, n -> 
                n instanceof com.github.javaparser.ast.stmt.BlockStmt);
            
            if (block.isPresent()) {
                com.github.javaparser.ast.stmt.BlockStmt blockStmt = (com.github.javaparser.ast.stmt.BlockStmt) block.get();
                
                // Find the index of the creation expression's statement
                int creationIndex = -1;
                List<com.github.javaparser.ast.stmt.Statement> statements = blockStmt.getStatements();
                for (int i = 0; i < statements.size(); i++) {
                    com.github.javaparser.ast.stmt.Statement stmt = statements.get(i);
                    if (containsCreationExpr(stmt, creationExpr)) {
                        creationIndex = i;
                        break;
                    }
                }
                
                if (creationIndex != -1) {
                    // Look for method calls on the variable in subsequent statements
                    for (int i = creationIndex + 1; i < statements.size(); i++) {
                        com.github.javaparser.ast.stmt.Statement stmt = statements.get(i);
                        
                        // Check if this is an expression statement with method call on our variable
                        if (stmt instanceof ExpressionStmt) {
                            ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                            if (exprStmt.getExpression() instanceof MethodCallExpr) {
                                MethodCallExpr mc = (MethodCallExpr) exprStmt.getExpression();
                                Optional<Expression> scope = mc.getScope();
                                if (scope.isPresent() && scope.get().toString().equals(varName)) {
                                    methodCalls.add(mc);
                                }
                            }
                        }
                        // Check if this is a return statement with method call on our variable
                        else if (stmt instanceof ReturnStmt) {
                            ReturnStmt returnStmt = (ReturnStmt) stmt;
                            Optional<Expression> returnExpr = returnStmt.getExpression();
                            if (returnExpr.isPresent() && returnExpr.get().toString().equals(varName)) {
                                // Variable is being returned, stop looking for more method calls
                                break;
                            }
                        }
                    }
                }
            }
            
            return methodCalls;
        }
        
        private boolean containsCreationExpr(Node node, ObjectCreationExpr target) {
            final boolean[] found = {false};
            node.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ObjectCreationExpr n, Void arg) {
                    if (n == target) {
                        found[0] = true;
                    }
                    super.visit(n, arg);
                }
            }, null);
            return found[0];
        }
        
        private String mapSetterToFluentMethod(String setterName) {
            // Map setter method names to fluent configuration method names
            switch (setterName) {
                case "setDataSource":
                    return "dataSource";
                case "setClassLoader":
                    return null; // ClassLoader is typically passed to configure() directly
                case "setLocations":
                    return "locations";
                case "setValidateOnMigrate":
                    return "validateOnMigrate";
                case "setBaselineOnMigrate":
                    return "baselineOnMigrate";
                case "setBaselineVersion":
                    return "baselineVersion";
                case "setEncoding":
                    return "encoding";
                case "setTable":
                    return "table";
                case "setSchemas":
                    return "schemas";
                case "setPlaceholders":
                    return "placeholders";
                case "setPlaceholderPrefix":
                    return "placeholderPrefix";
                case "setPlaceholderSuffix":
                    return "placeholderSuffix";
                case "setSqlMigrationPrefix":
                    return "sqlMigrationPrefix";
                case "setRepeatableSqlMigrationPrefix":
                    return "repeatableSqlMigrationPrefix";
                case "setSqlMigrationSuffixes":
                    return "sqlMigrationSuffixes";
                case "setTarget":
                    return "target";
                case "setOutOfOrder":
                    return "outOfOrder";
                case "setCleanDisabled":
                    return "cleanDisabled";
                case "setCleanOnValidationError":
                    return "cleanOnValidationError";
                case "setMixed":
                    return "mixed";
                case "setGroup":
                    return "group";
                case "setBatch":
                    return "batch";
                default:
                    return null; // Unknown setter, will need manual intervention
            }
        }
    }
}