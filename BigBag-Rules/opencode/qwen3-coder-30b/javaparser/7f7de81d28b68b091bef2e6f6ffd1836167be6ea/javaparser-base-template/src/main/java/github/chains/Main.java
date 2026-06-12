package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // Simple fix for snmp4j-agent 3.6.6 breaking change
        // This directly addresses the specific issue in the snmpman project
        try {
            // Process the specific file that has the issue
            Path filePath = Paths.get("/workspace/snmpman/snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java");
            
            if (Files.exists(filePath)) {
                processFile(filePath);
                System.out.println("Transformation completed successfully");
            } else {
                System.out.println("File not found: " + filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            ParseResult<CompilationUnit> result = JavaParser.parseResult(filePath.toFile());
            if (!result.isSuccessful()) {
                System.err.println("Failed to parse file: " + filePath);
                return;
            }
            
            CompilationUnit cu = result.getResult().get();
            
            // Create a visitor to find and fix the specific pattern
            cu.accept(new SnmpRegistryFixVisitor(), null);
            
            // Save the modified file
            try (OutputStream os = Files.newOutputStream(filePath)) {
                cu.write(os);
            }
            System.out.println("File processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static class SnmpRegistryFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(VariableDeclarationExpr variableDeclaration, Void arg) {
            super.visit(variableDeclaration, arg);
            
            // Look for the specific pattern: SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
            if (variableDeclaration.getVariables().size() == 1) {
                VariableDeclarator var = variableDeclaration.getVariables().get(0);
                if (var.getType() instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType type = (ClassOrInterfaceType) var.getType();
                    if (type.getNameAsString().equals("SortedMap")) {
                        // Check the type arguments 
                        if (type.getTypeArguments().isPresent() && type.getTypeArguments().get().size() >= 2) {
                            List<Type> typeArgs = type.getTypeArguments().get();
                            if (typeArgs.size() >= 2) {
                                // Check if second argument is ManagedObject without generics
                                Type secondArg = typeArgs.get(1);
                                if (secondArg instanceof ClassOrInterfaceType) {
                                    ClassOrInterfaceType secondType = (ClassOrInterfaceType) secondArg;
                                    if (secondType.getNameAsString().equals("ManagedObject")) {
                                        // Check if it has no generics - this is the problem
                                        if (!secondType.getTypeArguments().isPresent() || 
                                            secondType.getTypeArguments().get().isEmpty()) {
                                            // This is the problematic line - we need to change it to ManagedObject<?>
                                            secondType.setTypeArguments(NodeList.nodeList(new WildcardType()));
                                            System.out.println("Fixed SortedMap type in variable declaration");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
