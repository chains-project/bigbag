package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path file : javaFiles) {
            try (FileInputStream in = new FileInputStream(file.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                DubboApiMigrationVisitor visitor = new DubboApiMigrationVisitor();
                cu.accept(visitor, null);
                
                if (visitor.isModified()) {
                    Files.write(file, cu.toString().getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + file);
                }
            } catch (Exception e) {
                System.err.println("Error processing file: " + file + " - " + e.getMessage());
            }
        }
        
        System.out.println("Transformation complete. Modified " + transformedFiles + " files.");
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static class DubboApiMigrationVisitor extends ModifierVisitor<Void> {
        private boolean hasFutureAdapterImport = false;
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(CompilationUnit cu, Void arg) {
            // Check if FutureAdapter is imported
            hasFutureAdapterImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().contains("FutureAdapter"));
            return super.visit(cu, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for RpcContext.getContext().setFuture(AsyncRpcResult)
            if (isRpcContextSetFutureCall(n) || isFutureContextSetCompatibleFutureCall(n)) {
                Expression argument = n.getArgument(0);
                if (argument != null && shouldTransformArgument(argument)) {
                    // Create the transformation: argument.getResponseFuture()
                    MethodCallExpr getResponseFuture = new MethodCallExpr(argument.clone(), "getResponseFuture");
                    
                    // Wrap in FutureAdapter if imported, otherwise just use getResponseFuture()
                    if (hasFutureAdapterImport) {
                        ObjectCreationExpr futureAdapter = new ObjectCreationExpr();
                        futureAdapter.setType(new ClassOrInterfaceType(null, "FutureAdapter"));
                        futureAdapter.addArgument(getResponseFuture);
                        n.setArgument(0, futureAdapter);
                    } else {
                        n.setArgument(0, getResponseFuture);
                    }
                    modified = true;
                    return n;
                }
            }
            return super.visit(n, arg);
        }
        
        private boolean shouldTransformArgument(Expression argument) {
            String argStr = argument.toString();
            
            // Don't transform if already a CompletableFuture
            if (argStr.contains("CompletableFuture") || argStr.contains("getResponseFuture()") ||
                argStr.contains("FutureAdapter")) {
                return false;
            }
            
            // Transform if it looks like an AsyncRpcResult variable or creation
            // Common patterns: asyncRpcResult, result, rpcResult, etc.
            // Also check for AsyncRpcResult.newDefaultAsyncResult() calls
            if (argStr.matches("(?i).*async.*rpc.*result.*") ||
                argStr.contains("AsyncRpcResult") ||
                argStr.endsWith("Result") || argStr.endsWith("result") ||
                argStr.contains("newDefaultAsyncResult") ||
                argStr.contains("new AsyncRpcResult")) {
                return true;
            }
            
            // Also check if it's a variable that might be AsyncRpcResult
            // We'll be conservative and transform if it's a simple name (variable)
            // or a method call that returns AsyncRpcResult
            if (argument.isNameExpr() || argument.isMethodCallExpr()) {
                // For method calls, check if name suggests AsyncRpcResult
                if (argument.isMethodCallExpr()) {
                    MethodCallExpr call = argument.asMethodCallExpr();
                    String methodName = call.getNameAsString().toLowerCase();
                    if (methodName.contains("result") || methodName.contains("async")) {
                        return true;
                    }
                }
                return true; // Transform simple variables and method calls
            }
            
            return false;
        }
        
        private boolean isRpcContextSetFutureCall(MethodCallExpr n) {
            return n.getNameAsString().equals("setFuture") && 
                   n.getScope().isPresent() &&
                   isRpcContextGetContextCall(n.getScope().get());
        }
        
        private boolean isFutureContextSetCompatibleFutureCall(MethodCallExpr n) {
            return n.getNameAsString().equals("setCompatibleFuture") && 
                   n.getScope().isPresent() &&
                   isFutureContextGetContextCall(n.getScope().get());
        }
        
        private boolean isRpcContextGetContextCall(Expression expr) {
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                return methodCall.getNameAsString().equals("getContext") &&
                       methodCall.getScope().isPresent() &&
                       methodCall.getScope().get().toString().contains("RpcContext");
            }
            return false;
        }
        
        private boolean isFutureContextGetContextCall(Expression expr) {
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                return methodCall.getNameAsString().equals("getContext") &&
                       methodCall.getScope().isPresent() &&
                       methodCall.getScope().get().toString().contains("FutureContext");
            }
            return false;
        }
    }
}