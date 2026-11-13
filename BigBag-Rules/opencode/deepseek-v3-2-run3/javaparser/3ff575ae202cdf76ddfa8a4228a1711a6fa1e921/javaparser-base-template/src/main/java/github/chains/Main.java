package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.nio.file.*;
import java.util.*;

public class Main {
    
    // Configuration parameters - these would be externalized in a real tool
    private static final String BASE_INTERFACE = "org.codehaus.plexus.PlexusContainer";
    private static final String EXTENDED_INTERFACE = "org.codehaus.plexus.MutablePlexusContainer";
    
    // Methods that exist in EXTENDED_INTERFACE but not in BASE_INTERFACE
    private static final Set<String> METHODS_IN_EXTENDED_INTERFACE = new HashSet<>(Arrays.asList(
        "getLoggerManager",
        "getClassWorld", 
        "getComponentDiscovererManager",
        "getComponentFactoryManager",
        "getComponentRealm",
        "getComponentRegistry",
        "getConfiguration",
        "getConfigurationSource",
        "getLogger",
        "setComponentDiscovererManager",
        "setComponentFactoryManager", 
        "setComponentRegistry",
        "setConfiguration",
        "setConfigurationSource",
        "setLoggerManager"
    ));
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms method calls from " + BASE_INTERFACE + " to " + EXTENDED_INTERFACE);
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transforming calls to " + EXTENDED_INTERFACE + " methods on " + BASE_INTERFACE + " references");
        
        JavaParser parser = new JavaParser();
        
        // Walk through all Java files
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> processFile(p, parser));
        
        System.out.println("Transformation complete.");
    }
    
    private static void processFile(Path filePath, JavaParser parser) {
        try {
            System.out.println("Processing: " + filePath);
            
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
            
            // Create a visitor to modify method calls
            MethodCallTransformer visitor = new MethodCallTransformer();
            cu.accept(visitor, null);
            
            if (visitor.modified) {
                // Write back the modified file
                Files.writeString(filePath, cu.toString());
                System.out.println("  Modified: " + filePath + " (" + visitor.modifiedCount + " changes)");
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static class MethodCallTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        private int modifiedCount = 0;
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            // First, visit children
            super.visit(n, arg);
            
            String methodName = n.getNameAsString();
            
            // Check if this is a method that requires casting
            if (METHODS_IN_EXTENDED_INTERFACE.contains(methodName)) {
                // Check if scope is present
                if (n.getScope().isPresent()) {
                    Expression scope = n.getScope().get();
                    
                    // Check if scope might be of type BASE_INTERFACE
                    // In a full implementation, we would do proper type resolution here
                    // For this template, we use a heuristic approach
                    
                    // Heuristic 1: Check if scope is a method call to getContainer()
                    if (isGetContainerCall(scope)) {
                        return transformMethodCall(n, scope, methodName);
                    }
                    
                    // Heuristic 2: Check variable names that suggest PlexusContainer type
                    if (scope instanceof NameExpr) {
                        String varName = ((NameExpr) scope).getNameAsString();
                        if (varName.toLowerCase().contains("container")) {
                            return transformMethodCall(n, scope, methodName);
                        }
                    }
                    
                    // Heuristic 3: Check field access that suggests PlexusContainer type
                    if (scope instanceof FieldAccessExpr) {
                        String fieldName = ((FieldAccessExpr) scope).getNameAsString();
                        if (fieldName.toLowerCase().contains("container")) {
                            return transformMethodCall(n, scope, methodName);
                        }
                    }
                }
            }
            
            return n;
        }
        
        private boolean isGetContainerCall(Expression expr) {
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                return methodCall.getNameAsString().equals("getContainer");
            }
            return false;
        }
        
private MethodCallExpr transformMethodCall(MethodCallExpr original, Expression scope, String methodName) {
            System.out.println("    Transforming: " + methodName + "() call on " + scope);
            
            // Create cast expression: ((EXTENDED_INTERFACE) scope).methodName(...)
            // Need to wrap the cast in parentheses for proper precedence
            ClassOrInterfaceType castType = new ClassOrInterfaceType(null, EXTENDED_INTERFACE);
            CastExpr castExpr = new CastExpr(castType, scope.clone());
            
            // Create new method call with cast
            MethodCallExpr newCall = new MethodCallExpr(
                new EnclosedExpr(castExpr),  // Wrap in parentheses for proper precedence
                methodName,
                original.getArguments()
            );
            
            // Copy any comments
            original.getComment().ifPresent(newCall::setComment);
            
            modified = true;
            modifiedCount++;
            
            return newCall;
        }
    }
}