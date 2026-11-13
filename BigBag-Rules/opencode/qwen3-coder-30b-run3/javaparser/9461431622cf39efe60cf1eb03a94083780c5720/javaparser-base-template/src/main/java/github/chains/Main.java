package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.util.Collections;

/**
 * Generic transformation to fix snmp4j-agent API compatibility issues between 3.5.4 and 3.6.5
 * Specifically addresses the SortedMap<MOScope, ManagedObject> vs SortedMap<MOScope, ManagedObject<?>>
 * type mismatch in getRegistry() method calls and related patterns.
 */
public class Main {
    public static void main(String[] args) {
        // Process all Java files in the input directory
        if (args.length < 1) {
            System.err.println("Usage: java Main <input_directory>");
            System.exit(1);
        }

        String inputDir = args[0];
        
        try {
            SourceRoot sourceRoot = new SourceRoot(new File(inputDir));
            java.util.List<CompilationUnit> units = sourceRoot.parse("", (String)null);
            
            // Apply transformation to all parsed files
            for (CompilationUnit cu : units) {
                cu.accept(new Snmp4jAgentFixVisitor(), null);
            }
            
            // Save the modified files
            sourceRoot.saveAll();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that fixes the snmp4j-agent API compatibility issue
     * Changes: SortedMap<MOScope, ManagedObject> to SortedMap<MOScope, ManagedObject<?>>
     */
    private static class Snmp4jAgentFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Check if this is an assignment expression
            if (n.getExpression() instanceof AssignExpr) {
                AssignExpr assign = (AssignExpr) n.getExpression();
                
                // Check if we're assigning a getRegistry() call to a variable
                if (assign.getValue() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) assign.getValue();
                    if (methodCall.getNameAsString().equals("getRegistry") && 
                        methodCall.getScope().isPresent()) {
                        // This assignment is likely where the type mismatch occurs
                        // We don't modify it directly here - we fix the type pattern elsewhere
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ParameterizedType n, Void arg) {
            // Look for the specific problematic pattern: SortedMap<MOScope, ManagedObject>
            // and change it to: SortedMap<MOScope, ManagedObject<?>>
            
            // Only process if this is a SortedMap type
            if (n.getNameAsString().equals("SortedMap")) {
                // Check if it has exactly 2 type arguments
                if (n.getTypeArguments().size() >= 2) {
                    // Get the second type argument (the value type)
                    Type secondArg = n.getTypeArguments().get(1);
                    
                    // Check if it's the problematic ManagedObject without wildcard
                    if (secondArg instanceof ClassOrInterfaceType) {
                        ClassOrInterfaceType type = (ClassOrInterfaceType) secondArg;
                        if (type.getNameAsString().equals("ManagedObject") && 
                            type.getTypeArguments().isEmpty()) {
                            
                            // Create a new type with wildcard: ManagedObject<?>
                            WildcardType wildcardType = new WildcardType();
                            ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "ManagedObject", 
                                Collections.singletonList(wildcardType));
                            
                            // Replace the type argument
                            n.getTypeArguments().set(1, newType);
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}

        String inputDir = args[0];
        
        try {
            SourceRoot sourceRoot = new SourceRoot(new File(inputDir));
            java.util.List<CompilationUnit> units = sourceRoot.parse("", Collections.emptyList());
            
            // Apply transformation to all parsed files
            for (CompilationUnit cu : units) {
                cu.accept(new Snmp4jAgentFixVisitor(), null);
            }
            
            // Save the modified files
            sourceRoot.saveAll();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that fixes the snmp4j-agent API compatibility issue
     * Changes: SortedMap<MOScope, ManagedObject> to SortedMap<MOScope, ManagedObject<?>>
     */
    private static class Snmp4jAgentFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Check if this is an assignment expression
            if (n.getExpression() instanceof AssignmentExpr) {
                AssignmentExpr assign = (AssignmentExpr) n.getExpression();
                
                // Check if we're assigning a getRegistry() call to a variable
                if (assign.getValue() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) assign.getValue();
                    if (methodCall.getNameAsString().equals("getRegistry") && 
                        methodCall.getScope().isPresent()) {
                        
                        // Check the target variable's type declaration
                        if (assign.getTarget() instanceof NameExpr) {
                            // This assignment is likely where the type mismatch occurs
                            // We don't modify it directly here because we want to be more 
                            // specific about handling the type declarations
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ParameterizedType n, Void arg) {
            // Look for the specific problematic pattern: SortedMap<MOScope, ManagedObject>
            // and change it to: SortedMap<MOScope, ManagedObject<?>>
            if (n.getTypeArguments().size() >= 2) {
                // Try to identify the pattern that needs fixing
                Type firstArg = n.getTypeArguments().get(1);
                
                // Check if it's the ManagedObject without wildcard
                if (firstArg instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType type = (ClassOrInterfaceType) firstArg;
                    if (type.getNameAsString().equals("ManagedObject") && 
                        type.getTypeArguments().isEmpty()) {
                        
                        // Create a new type with wildcard: ManagedObject<?>
                        WildcardType wildcardType = new WildcardType();
                        ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "ManagedObject", 
                            Collections.singletonList(wildcardType));
                        
                        // Replace the type argument
                        n.getTypeArguments().set(1, newType);
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}
