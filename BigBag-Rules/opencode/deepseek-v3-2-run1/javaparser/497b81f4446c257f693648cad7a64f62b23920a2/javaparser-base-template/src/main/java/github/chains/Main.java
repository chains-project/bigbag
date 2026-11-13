package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }
    
    private static void processFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + file)
            );
            
            boolean modified = false;
            
            // Remove org.cactoos imports
            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.startsWith("org.cactoos.io") || 
                    importName.startsWith("org.cactoos.text") ||
                    importName.startsWith("org.cactoos.list")) {
                    importsToRemove.add(importDecl);
                    modified = true;
                }
            }
            importsToRemove.forEach(Node::remove);
            
            // Add java.util.Arrays import if needed (for ListOf replacement)
            boolean needsArraysImport = cu.findAll(ObjectCreationExpr.class).stream()
                .anyMatch(expr -> expr.getType().toString().startsWith("ListOf"));
            
            // Check if import already exists
            boolean hasArraysImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("java.util.Arrays"));
            
            if (needsArraysImport && !hasArraysImport) {
                cu.addImport("java.util.Arrays");
                modified = true;
            }
            
            // Add java.util.HexFormat import if needed (for HexOf replacement)
            boolean needsHexFormatImport = needsHexFormatImport(cu);
            
            // Check if import already exists
            boolean hasHexFormatImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("java.util.HexFormat"));
            
            if (needsHexFormatImport && !hasHexFormatImport) {
                cu.addImport("java.util.HexFormat");
                modified = true;
            }
            
            // Transform HexOf(BytesOf(...)).asString() patterns
            CactoosTransformer transformer = new CactoosTransformer();
            cu.accept(transformer, null);
            modified = modified || transformer.isModified();
            
            if (modified) {
                Files.write(file, cu.toString().getBytes());
                System.out.println("Modified: " + file);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean needsHexFormatImport(CompilationUnit cu) {
        // Check if file contains HexOf(BytesOf(...)).asString() pattern
        return cu.findAll(MethodCallExpr.class).stream()
            .anyMatch(expr -> {
                if (expr.getNameAsString().equals("asString")) {
                    com.github.javaparser.ast.expr.Expression scope = expr.getScope().orElse(null);
                    if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr hexOfExpr = (ObjectCreationExpr) scope;
                        if (hexOfExpr.getType().asString().equals("HexOf")) {
                            return true;
                        }
                    }
                }
                return false;
            });
    }
    
    private static class CactoosTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Handle ListOf replacement
            // Check if type name starts with ListOf (could be ListOf<...>)
            String typeName = expr.getType().toString();
            if (typeName.startsWith("ListOf")) {
                modified = true;
                // Create Arrays.asList(...) call
                MethodCallExpr asListCall = new MethodCallExpr();
                
                // Create scope: Arrays
                com.github.javaparser.ast.expr.NameExpr arraysName = 
                    new com.github.javaparser.ast.expr.NameExpr("Arrays");
                asListCall.setScope(arraysName);
                asListCall.setName("asList");
                
                NodeList<com.github.javaparser.ast.expr.Expression> arguments = expr.getArguments();
                if (arguments != null) {
                    arguments.forEach(asListCall::addArgument);
                }
                
                return asListCall;
            }
            
            // Handle HexOf(BytesOf(...)).asString() pattern
            // We'll handle this in visit(MethodCallExpr) to catch the full chain
            
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr expr, Void arg) {
            // Check for HexOf(BytesOf(...)).asString() pattern
            if (expr.getNameAsString().equals("asString")) {
                com.github.javaparser.ast.expr.Expression scope = expr.getScope().orElse(null);
                if (scope instanceof ObjectCreationExpr) {
                    ObjectCreationExpr hexOfExpr = (ObjectCreationExpr) scope;
                    if (hexOfExpr.getType().asString().equals("HexOf")) {
                        // Check if HexOf has a BytesOf argument
                        if (hexOfExpr.getArguments().size() == 1) {
                            com.github.javaparser.ast.expr.Expression hexArg = hexOfExpr.getArgument(0);
                            if (hexArg instanceof ObjectCreationExpr) {
                                ObjectCreationExpr bytesOfExpr = (ObjectCreationExpr) hexArg;
                                if (bytesOfExpr.getType().asString().equals("BytesOf")) {
                                    // This is HexOf(BytesOf(...)).asString()
                                    modified = true;
                                    
                                    // Get the argument to BytesOf (the byte array)
                                    com.github.javaparser.ast.expr.Expression bytesArg = 
                                        bytesOfExpr.getArguments().size() > 0 ? 
                                        bytesOfExpr.getArgument(0) : null;
                                    
                                    if (bytesArg != null) {
                                        // Replace with HexFormat.of().formatHex(bytes)
                                        MethodCallExpr ofCall = new MethodCallExpr();
                                        com.github.javaparser.ast.expr.NameExpr hexFormatName = 
                                            new com.github.javaparser.ast.expr.NameExpr("HexFormat");
                                        ofCall.setScope(hexFormatName);
                                        ofCall.setName("of");
                                        
                                        MethodCallExpr formatHexCall = new MethodCallExpr();
                                        formatHexCall.setScope(ofCall);
                                        formatHexCall.setName("formatHex");
                                        formatHexCall.addArgument(bytesArg);
                                        
                                        return formatHexCall;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return super.visit(expr, arg);
        }
    }
}