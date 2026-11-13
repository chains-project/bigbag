package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for Jetty 11 breaking changes.
 * This transformation handles:
 * 1. javax.servlet -> jakarta.servlet package migration
 * 2. SelectChannelConnector -> ServerConnector class replacement
 * 3. Removal of deprecated Server methods: setSendServerVersion, setSendDateHeader
 * 
 * The transformation is parameterized and can be extended for other breaking changes.
 */
public class Main {
    
    // Configuration: Old -> New mappings
    private static final Map<String, String> IMPORT_REPLACEMENTS = new HashMap<>();
    private static final Map<String, String> TYPE_REPLACEMENTS = new HashMap<>();
    private static final Map<String, String> METHOD_REMOVALS = new HashMap<>();
    
    static {
        // Configure the breaking changes for Jetty 11
        IMPORT_REPLACEMENTS.put("javax.servlet", "jakarta.servlet");
        
        TYPE_REPLACEMENTS.put("SelectChannelConnector", "ServerConnector");
        TYPE_REPLACEMENTS.put("javax.servlet.http.HttpServletRequest", "jakarta.servlet.http.HttpServletRequest");
        TYPE_REPLACEMENTS.put("javax.servlet.http.HttpServletResponse", "jakarta.servlet.http.HttpServletResponse");
        TYPE_REPLACEMENTS.put("javax.servlet.ServletException", "jakarta.servlet.ServletException");
        
        METHOD_REMOVALS.put("setSendServerVersion", "Method removed in Jetty 11. Configure via HttpConfiguration instead.");
        METHOD_REMOVALS.put("setSendDateHeader", "Method removed in Jetty 11. Configure via HttpConfiguration instead.");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("\nThis transformation handles Jetty 11 breaking changes:");
            System.err.println("• javax.servlet -> jakarta.servlet package migration");
            System.err.println("• SelectChannelConnector -> ServerConnector replacement");
            System.err.println("• Removal of setSendServerVersion() and setSendDateHeader()");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jetty 11 migration transformation to: " + sourceDir);
        System.out.println("Transformations configured:");
        System.out.println("  Import replacements: " + IMPORT_REPLACEMENTS);
        System.out.println("  Type replacements: " + TYPE_REPLACEMENTS);
        System.out.println("  Method removals: " + METHOD_REMOVALS.keySet());
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        JavaParser parser = new JavaParser();
        JettyMigrationVisitor visitor = new JettyMigrationVisitor();
        
        int transformedCount = 0;
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
                int changes = visitor.resetChanges();
                cu.accept(visitor, null);
                
                if (visitor.getChangeCount() > changes) {
                    // Write back transformed file
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                    String transformed = printer.print(cu);
                    Files.write(javaFile, transformed.getBytes());
                    transformedCount++;
                    System.out.println("Transformed (" + visitor.getChangeCount() + " changes): " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete.");
        System.out.println("Processed " + javaFiles.size() + " files, transformed " + transformedCount + " files.");
        System.out.println("Total changes applied: " + visitor.getTotalChanges());
    }
    
    /**
     * Generic visitor for Jetty migration transformations.
     * Applies configured replacements and removals.
     */
    static class JettyMigrationVisitor extends ModifierVisitor<Void> {
        private int changeCount = 0;
        private int totalChanges = 0;
        
        public int resetChanges() {
            int old = changeCount;
            changeCount = 0;
            return old;
        }
        
        public int getChangeCount() {
            return changeCount;
        }
        
        public int getTotalChanges() {
            return totalChanges;
        }
        
        private void recordChange() {
            changeCount++;
            totalChanges++;
        }
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            String importName = id.getNameAsString();
            
            // Apply import replacements
            for (Map.Entry<String, String> replacement : IMPORT_REPLACEMENTS.entrySet()) {
                if (importName.startsWith(replacement.getKey())) {
                    String newImport = importName.replace(replacement.getKey(), replacement.getValue());
                    id.setName(newImport);
                    recordChange();
                    System.out.println("  [Import] " + importName + " -> " + newImport);
                    break;
                }
            }
            
            return (Node) super.visit(id, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            String fullTypeName = typeName;
            
            // Check for fully qualified type replacements
            for (Map.Entry<String, String> replacement : TYPE_REPLACEMENTS.entrySet()) {
                if (replacement.getKey().contains(".") && replacement.getKey().endsWith(typeName)) {
                    // This is a fully qualified type name replacement
                    // We'd need scope resolution to be certain, but we can check if it matches
                    fullTypeName = replacement.getKey();
                }
            }
            
            // Apply type replacements
            if (TYPE_REPLACEMENTS.containsKey(fullTypeName)) {
                String newType = TYPE_REPLACEMENTS.get(fullTypeName);
                // Extract simple name if it was fully qualified
                if (newType.contains(".")) {
                    newType = newType.substring(newType.lastIndexOf('.') + 1);
                }
                type.setName(newType);
                recordChange();
                System.out.println("  [Type] " + fullTypeName + " -> " + newType);
            } else if (TYPE_REPLACEMENTS.containsKey(typeName)) {
                // Simple name replacement
                String newType = TYPE_REPLACEMENTS.get(typeName);
                type.setName(newType);
                recordChange();
                System.out.println("  [Type] " + typeName + " -> " + newType);
            }
            
            return (Node) super.visit(type, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            String typeName = expr.getType().asString();
            
            // SelectChannelConnector -> ServerConnector transformation
            if ("SelectChannelConnector".equals(typeName)) {
                expr.setType("ServerConnector");
                recordChange();
                System.out.println("  [Constructor] SelectChannelConnector -> ServerConnector");
                
                // SelectChannelConnector() constructor needs Server parameter
                NodeList<com.github.javaparser.ast.expr.Expression> args = expr.getArguments();
                if (args.isEmpty()) {
                    // In Jetty 11, ServerConnector requires Server parameter
                    // We add 'server' assuming it's in scope (common pattern)
                    args.add(new NameExpr("server"));
                    recordChange();
                    System.out.println("  [Constructor] Added 'server' parameter to ServerConnector");
                }
            }
            
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodDeclaration md, Void arg) {
            // Update method parameter types
            NodeList<Parameter> params = md.getParameters();
            for (Parameter param : params) {
                Type paramType = param.getType();
                if (paramType.isClassOrInterfaceType()) {
                    ClassOrInterfaceType classType = paramType.asClassOrInterfaceType();
                    String typeName = classType.getNameAsString();
                    
                    // Check and update javax.servlet types
                    if ("HttpServletRequest".equals(typeName) || 
                        "HttpServletResponse".equals(typeName) ||
                        "ServletException".equals(typeName)) {
                        // Note: We'd need imports analysis to be certain it's from javax.servlet
                        // For this generic transformation, we update based on common patterns
                        recordChange();
                        System.out.println("  [Parameter] Potential javax.servlet type in method " + md.getNameAsString());
                    }
                }
            }
            
            return (Node) super.visit(md, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Remove calls to deprecated/removed methods
            if (METHOD_REMOVALS.containsKey(methodName)) {
                String reason = METHOD_REMOVALS.get(methodName);
                System.out.println("  [Method Removal] " + methodName + "(): " + reason);
                
                // Remove the entire statement if it's a standalone expression
                if (expr.getParentNode().isPresent() && 
                    expr.getParentNode().get() instanceof ExpressionStmt) {
                    recordChange();
                    return null;
                }
            }
            
            return (Node) super.visit(expr, arg);
        }
    }
}