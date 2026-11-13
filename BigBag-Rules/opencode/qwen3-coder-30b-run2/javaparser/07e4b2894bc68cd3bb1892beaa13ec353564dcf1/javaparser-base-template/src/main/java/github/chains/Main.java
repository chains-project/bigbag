package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) {
        // This is a generic transformation that handles method signature changes
        // in Jenkins acceptance test harness or similar libraries
        
        if (args.length < 2) {
            System.out.println("Usage: java Main <source-directory> <transformation-type>");
            System.out.println("Transformation types:");
            System.out.println("  method-signature-change");
            return;
        }
        
        String sourceDirectory = args[0];
        String transformationType = args[1];
        
        switch(transformationType) {
            case "method-signature-change":
                transformMethodSignatureChange(sourceDirectory);
                break;
            default:
                System.out.println("Unknown transformation type: " + transformationType);
        }
    }
    
    public static void transformMethodSignatureChange(String directoryPath) {
        try {
            Files.walk(Paths.get(directoryPath))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformJavaFileForMethodSignatureChange);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void transformJavaFileForMethodSignatureChange(String filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath));
            
            // Apply method signature transformation
            cu.accept(new MethodSignatureChangeTransformer(), null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(cu.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            // Don't fail completely on individual file errors
        }
    }
    
    /**
     * Transformer that handles common method signature breaking changes
     * This is a generic template that can be configured for specific breaking changes
     */
    private static class MethodSignatureChangeTransformer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Example: Handle a breaking change from:
            // oldMethod(param1, param2) 
            // to:
            // newMethod(param1, true, param2)
            //
            // This would be customized for specific scenarios
            
            // Pattern matching for specific common breaking changes
            // This example handles a signature change from 2 params to 3 params
            // with a boolean parameter inserted in the middle
            
            String methodName = methodCall.getNameAsString();
            
            // Example transformation for a common breaking change pattern
            if (methodName.equals("oldMethod") || methodName.equals("deprecatedMethod")) {
                NodeList<Expression> arguments = methodCall.getArguments();
                
                // If we have exactly 2 arguments, insert a new boolean parameter
                if (arguments.size() == 2) {
                    // Insert a new parameter (true) at position 1 (middle)
                    Expression newParam = new BooleanLiteralExpr(true);
                    arguments.add(1, newParam);
                }
            }
            
            // Another example - if a method used to take a String but now takes a boolean
            if (methodName.equals("processString")) {
                NodeList<Expression> arguments = methodCall.getArguments();
                if (arguments.size() == 1) {
                    // Change String literal to boolean literal
                    Expression arg = arguments.get(0);
                    if (arg instanceof StringLiteralExpr) {
                        // Replace with boolean literal (assuming true as default)
                        arguments.set(0, new BooleanLiteralExpr(true));
                    }
                }
            }
        }
    }
}