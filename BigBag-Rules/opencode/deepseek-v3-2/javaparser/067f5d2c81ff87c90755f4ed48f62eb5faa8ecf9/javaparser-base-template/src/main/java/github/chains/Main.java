package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.*;
import java.util.*;

/**
 * GENERIC JavaParser transformation for fixing breaking dependency updates
 * where constructors have changed to throw checked exceptions.
 * 
 * Configuration: Modify BREAKING_CONSTRUCTORS map to add constructor patterns
 * that now throw checked exceptions.
 * 
 * Pattern: Map<FullyQualifiedClassName, List<ConstructorConfig>>
 * ConstructorConfig: (SimpleClassName, List<ParameterTypeNames>)
 * 
 * The transformation handles:
 * 1. Local variable declarations - wraps in try-catch
 * 2. Field initializers - adds TODO comment (needs manual fix)
 * 3. Other expressions - wraps in try-catch
 */
public class Main {
    
    // === CONFIGURATION: Add breaking constructors here ===
    // Format: Map<FullyQualifiedClassName, List<ConstructorConfig>>
    private static final Map<String, List<ConstructorConfig>> BREAKING_CONSTRUCTORS = new HashMap<>();
    
    static {
        // Example: org.apache.thrift.TSerializer constructors in libthrift 0.16.0
        BREAKING_CONSTRUCTORS.put("org.apache.thrift.TSerializer", Arrays.asList(
            new ConstructorConfig("TSerializer", Collections.emptyList()),
            new ConstructorConfig("TSerializer", Arrays.asList("org.apache.thrift.protocol.TProtocolFactory"))
        ));
        
        // Example: org.apache.thrift.TDeserializer constructors in libthrift 0.16.0
        BREAKING_CONSTRUCTORS.put("org.apache.thrift.TDeserializer", Arrays.asList(
            new ConstructorConfig("TDeserializer", Collections.emptyList()),
            new ConstructorConfig("TDeserializer", Arrays.asList("org.apache.thrift.protocol.TProtocolFactory"))
        ));
    }
    
    /**
     * Configuration for a constructor that now throws checked exceptions
     */
    private static class ConstructorConfig {
        final String className;
        final List<String> paramTypes;
        
        ConstructorConfig(String className, List<String> paramTypes) {
            this.className = className;
            this.paramTypes = paramTypes;
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Applies generic transformations for breaking constructor changes");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== JavaParser Breaking Constructor Fixer ===");
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Configured constructors: " + BREAKING_CONSTRUCTORS.keySet());
        System.out.println("==============================================");
        
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> processFile(path.toFile()));
        
        System.out.println("\nTransformation complete!");
        System.out.println("NOTE: Field initializers require manual fixes (moved to constructors)");
        System.out.println("      Look for TODO comments in modified files.");
    }
    
    private static void processFile(File file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + file);
                return;
            }
            
            BreakingConstructorVisitor visitor = new BreakingConstructorVisitor();
            cu.accept(visitor, null);
            
            if (visitor.modified) {
                // Write the modified file
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String newContent = printer.print(cu);
                
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(newContent);
                }
                
                System.out.println("Modified: " + file + " (" + visitor.fixesApplied + " fixes)");
            }
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and fixes breaking constructor calls
     */
    private static class BreakingConstructorVisitor extends ModifierVisitor<Void> {
        boolean modified = false;
        int fixesApplied = 0;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a constructor call for a breaking constructor
            String typeName = getFullyQualifiedTypeName(n);
            
            if (typeName != null && BREAKING_CONSTRUCTORS.containsKey(typeName)) {
                // Check if this matches one of our configured constructors
                ConstructorConfig matchingConfig = findMatchingConstructor(typeName, n);
                if (matchingConfig != null) {
                    // Found a breaking constructor call that needs transformation
                    Node result = transformConstructorCall(n, arg);
                    if (result != n) {
                        fixesApplied++;
                        modified = true;
                        return result;
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        /**
         * Determine fully qualified type name from object creation expression
         */
        private String getFullyQualifiedTypeName(ObjectCreationExpr expr) {
            if (expr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) expr.getType();
                String simpleName = type.getNameAsString();
                
                // Match by simple name (allowing for imports)
                for (String fqName : BREAKING_CONSTRUCTORS.keySet()) {
                    String targetSimpleName = fqName.substring(fqName.lastIndexOf('.') + 1);
                    if (targetSimpleName.equals(simpleName)) {
                        return fqName;
                    }
                }
            }
            return null;
        }
        
        /**
         * Find matching constructor configuration based on parameter count
         * (Note: For precise matching, check parameter types as well)
         */
        private ConstructorConfig findMatchingConstructor(String typeName, ObjectCreationExpr expr) {
            List<ConstructorConfig> configs = BREAKING_CONSTRUCTORS.get(typeName);
            if (configs == null) return null;
            
            for (ConstructorConfig config : configs) {
                if (config.paramTypes.size() == expr.getArguments().size()) {
                    // Simple match by parameter count
                    // In production, add type checking for exact match
                    return config;
                }
            }
            
            return null;
        }
        
        /**
         * Transform constructor call based on context
         */
        private Node transformConstructorCall(ObjectCreationExpr expr, Void arg) {
            // Get the parent node to understand context
            Node parent = expr.getParentNode().orElse(null);
            
            if (parent instanceof VariableDeclarator) {
                VariableDeclarator declarator = (VariableDeclarator) parent;
                Node grandParent = parent.getParentNode().orElse(null);
                
                if (grandParent instanceof FieldDeclaration) {
                    // FIELD INITIALIZER - requires manual fix
                    return handleFieldInitializer((FieldDeclaration) grandParent, declarator, expr);
                } else {
                    // LOCAL VARIABLE DECLARATION - wrap in try-catch
                    return handleLocalVariableDeclaration(declarator, expr);
                }
            } else {
                // OTHER CONTEXT (method args, return values, etc.) - wrap in try-catch
                return handleOtherContext(expr);
            }
        }
        
        /**
         * Handle field initializers (cannot have try-catch in initializer)
         * Adds TODO comment and sets to null - requires manual fix
         */
        private Node handleFieldInitializer(FieldDeclaration fieldDecl, 
                                           VariableDeclarator declarator, 
                                           ObjectCreationExpr expr) {
            System.err.println("WARNING: Field initialization at " + getLocation(expr) + 
                             " needs manual fix (move to constructor).");
            
            // Replace with null and add TODO comment
            NodeList<VariableDeclarator> variables = fieldDecl.getVariables();
            for (int i = 0; i < variables.size(); i++) {
                if (variables.get(i) == declarator) {
                    VariableDeclarator newDecl = declarator.clone();
                    newDecl.setInitializer(new NullLiteralExpr());
                    variables.set(i, newDecl);
                    
                    // Add TODO comment
                    String comment = " TODO: Fix field initialization - " + 
                        expr.getType().asString() + " constructor throws checked exception";
                    if (fieldDecl.getComment().isPresent()) {
                        comment = fieldDecl.getComment().get().getContent() + "\n" + comment;
                    }
                    fieldDecl.setComment(new LineComment(comment));
                    
                    return fieldDecl;
                }
            }
            
            return fieldDecl;
        }
        
        /**
         * Handle local variable declarations - wrap in try-catch
         */
        private Statement handleLocalVariableDeclaration(VariableDeclarator declarator, 
                                                       ObjectCreationExpr expr) {
            // Create try-catch block
            TryStmt tryStmt = new TryStmt();
            
            // Create variable declaration with initializer inside try block
            VariableDeclarator newDeclarator = declarator.clone();
            newDeclarator.setInitializer(expr.clone());
            VariableDeclarationExpr varDecl = new VariableDeclarationExpr(newDeclarator);
            
            BlockStmt tryBlock = new BlockStmt();
            tryBlock.addStatement(new ExpressionStmt(varDecl));
            tryStmt.setTryBlock(tryBlock);
            
            // Add catch block
            CatchClause catchClause = createCatchClause(expr);
            tryStmt.setCatchClauses(new NodeList<>(catchClause));
            
            return tryStmt;
        }
        
        /**
         * Handle other contexts (method arguments, return values, etc.)
         */
        private Statement handleOtherContext(ObjectCreationExpr expr) {
            // Wrap expression in try-catch block
            TryStmt tryStmt = new TryStmt();
            
            BlockStmt tryBlock = new BlockStmt();
            tryBlock.addStatement(new ExpressionStmt(expr.clone()));
            tryStmt.setTryBlock(tryBlock);
            
            CatchClause catchClause = createCatchClause(expr);
            tryStmt.setCatchClauses(new NodeList<>(catchClause));
            
            return tryStmt;
        }
        
        /**
         * Create a catch clause for the appropriate exception type
         * Generic implementation catches Exception - customize as needed
         */
        private CatchClause createCatchClause(ObjectCreationExpr expr) {
            // Determine exception type from API specification
            // For thrift, it's TTransportException
            String exceptionType = "Exception"; // Default
            String typeName = getFullyQualifiedTypeName(expr);
            
            if (typeName != null) {
                if (typeName.contains("thrift")) {
                    exceptionType = "org.apache.thrift.transport.TTransportException";
                }
            }
            
            Parameter param = new Parameter(
                new ClassOrInterfaceType(exceptionType),
                "e"
            );
            
            BlockStmt catchBlock = new BlockStmt();
            // Generic handler - rethrow as RuntimeException
            // Projects should customize this based on their error handling
            ThrowStmt throwStmt = new ThrowStmt(
                new ObjectCreationExpr(
                    null,
                    new ClassOrInterfaceType("RuntimeException"),
                    new NodeList<>(new NameExpr("e"))
                )
            );
            catchBlock.addStatement(throwStmt);
            
            return new CatchClause(param, catchBlock);
        }
        
        private String getLocation(Node node) {
            return node.getRange().map(range -> range.begin.toString()).orElse("unknown");
        }
    }
}