package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Optional;

/**
 * Generic transformation for Connection.accept method signature change.
 * Transforms calls from: connection.accept(status, iterable, publisher)
 * To: connection.accept(status, new Headers.From(iterable), publisher)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-file>");
            System.exit(1);
        }
        
        String filePath = args[0];
        
        // Parse the file
        FileInputStream in = new FileInputStream(filePath);
        CompilationUnit cu = StaticJavaParser.parse(in);
        in.close();
        
        // Apply transformation
        cu.accept(new ConnectionAcceptVisitor(), null);
        
        // Save the modified file
        FileWriter writer = new FileWriter(filePath);
        writer.write(cu.toString());
        writer.close();
        
        System.out.println("Transformation completed.");
    }
    
    private static class ConnectionAcceptVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a call to accept method with 3 arguments
            if ("accept".equals(methodCall.getNameAsString()) && 
                methodCall.getArguments().size() == 3) {
                
                // Look at the scope to see if it's likely a Connection object
                Optional<Expression> scope = methodCall.getScope();
                if (!scope.isPresent()) {
                    // Not a method call on an object, skip
                    return;
                }
                
                Expression expr = scope.get();
                
                // Check if it's a simple name expression (like "connection") or field access
                boolean isLikelyConnection = false;
                if (expr instanceof NameExpr) {
                    // This is a simple name - proceed with transformation (conservative approach)
                    isLikelyConnection = true;
                } else if (expr instanceof FieldAccessExpr) {
                    // Field access like "someObj.connection" - likely a connection
                    isLikelyConnection = true;
                } else {
                    // For other cases, we'll skip for safety
                    return;
                }
                
                if (isLikelyConnection) {
                    // Check if the second argument is already wrapped - prevent double-wrapping
                    NodeList<Expression> args = methodCall.getArguments();
                    Expression secondArg = args.get(1);
                    
                    // Check if it's already wrapped
                    if (secondArg instanceof ObjectCreationExpr) {
                        ObjectCreationExpr objCreation = (ObjectCreationExpr) secondArg;
                        if (objCreation.getType().getNameAsString().equals("Headers.From")) {
                            // Already transformed, skip
                            return;
                        }
                    }
                    
                    // Also check if it's any kind of Headers constructor or similar
                    // This is a simplified check - in practice we won't do recursive unwrapping
                    
                    // Transform the method call
                    Expression statusArg = args.get(0);
                    Expression iterableArg = args.get(1); 
                    Expression publisherArg = args.get(2);
                    
                    // Create new Headers.From(iterableArg) expression 
                    ObjectCreationExpr headersFrom = new ObjectCreationExpr();
                    headersFrom.setType(new ClassOrInterfaceType(null, "Headers.From"));
                    NodeList<Expression> headersArgs = new NodeList<>();
                    headersArgs.add(iterableArg);
                    headersFrom.setArguments(headersArgs);
                    
                    // Update the method call with the new arguments
                    args.set(1, headersFrom);
                }
            }
        }
    }
}