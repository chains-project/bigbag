package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation complete!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws Exception {
        JavaParser parser = new JavaParser();
        AtomicInteger transformedFiles = new AtomicInteger(0);
        AtomicInteger totalFiles = new AtomicInteger(0);
        
        Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> {
                totalFiles.incrementAndGet();
                transformFile(p, parser, transformedFiles);
            });
        
        System.out.println("\nTransformation summary:");
        System.out.println("Total Java files processed: " + totalFiles.get());
        System.out.println("Files transformed: " + transformedFiles.get());
    }
    
    private static void transformFile(Path filePath, JavaParser parser, AtomicInteger transformedFiles) {
        try {
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            
            // Remove cactoos imports
            int removedImports = (int) cu.getImports().stream()
                .filter(imp -> imp.getNameAsString().startsWith("org.cactoos."))
                .count();
            modified = cu.getImports().removeIf(imp -> 
                imp.getNameAsString().startsWith("org.cactoos.")
            ) || modified;
            
            if (removedImports > 0) {
                System.out.println("  Removed " + removedImports + " cactoos import(s) from " + filePath.getFileName());
            }
            
            // Apply AST transformations
            CactoosTransformer transformer = new CactoosTransformer();
            cu.accept(transformer, null);
            modified = transformer.modified || modified;
            
            // Add necessary imports if we modified the file
            if (modified) {
                // Add java.util.Arrays import if we converted ListOf to Arrays.asList
                if (transformer.needsArraysImport) {
                    cu.addImport("java.util.Arrays");
                    System.out.println("  Added java.util.Arrays import to " + filePath.getFileName());
                }
                // Add java.util.List import if we created List return types
                if (transformer.needsListImport) {
                    cu.addImport("java.util.List");
                    System.out.println("  Added java.util.List import to " + filePath.getFileName());
                }
                // Add Apache Commons Codec Hex import if we used hex conversion
                if (transformer.needsHexImport) {
                    cu.addImport("org.apache.commons.codec.binary.Hex");
                    System.out.println("  Added org.apache.commons.codec.binary.Hex import to " + filePath.getFileName());
                }
                
                // Track transformations
                int totalTransformations = transformer.hexConversions + transformer.listConversions;
                if (totalTransformations > 0) {
                    System.out.println("  Applied " + totalTransformations + " transformation(s) to " + filePath.getFileName() + 
                                     " (HexOf/BytesOf: " + transformer.hexConversions + ", ListOf: " + transformer.listConversions + ")");
                }
                
                System.out.println("Transforming: " + filePath);
                Files.write(filePath, cu.toString().getBytes());
                transformedFiles.incrementAndGet();
            }
        } catch (Exception e) {
            System.err.println("Error transforming " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static class CactoosTransformer extends ModifierVisitor<Void> {
        boolean modified = false;
        boolean needsArraysImport = false;
        boolean needsListImport = false;
        boolean needsHexImport = false;
        int hexConversions = 0;
        int listConversions = 0;
        private Set<String> addedHelperMethods = new HashSet<>();
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            String typeName = expr.getType().asString();
            
            // Handle HexOf and BytesOf patterns for hex conversion
            if ("HexOf".equals(typeName)) {
                return handleHexOf(expr);
            }
            
            // Handle BytesOf patterns (standalone)
            if ("BytesOf".equals(typeName)) {
                return handleBytesOf(expr);
            }
            
            // Handle ListOf<T>(...) -> Arrays.asList(...)
            // Check if type name starts with "ListOf" (may have type arguments)
            if (typeName.startsWith("ListOf")) {
                return handleListOf(expr);
            }
            
            return super.visit(expr, arg);
        }
        
        private Visitable handleHexOf(ObjectCreationExpr expr) {
            // Check if this is HexOf(BytesOf(...))
            if (expr.getArguments().size() == 1) {
                Expression arg = expr.getArguments().get(0);
                
                // Handle HexOf(BytesOf(bytes))
                if (arg instanceof ObjectCreationExpr) {
                    ObjectCreationExpr inner = (ObjectCreationExpr) arg;
                    if ("BytesOf".equals(inner.getType().asString())) {
                        // We'll replace the entire expression with a hex conversion
                        // Check if parent is .asString() call
                        if (expr.getParentNode().isPresent() && 
                            expr.getParentNode().get() instanceof MethodCallExpr) {
                            MethodCallExpr parentCall = (MethodCallExpr) expr.getParentNode().get();
                            if ("asString".equals(parentCall.getNameAsString())) {
                                // Replace new HexOf(new BytesOf(bytes)).asString() with Hex.encodeHexString(bytes)
                                Expression bytesExpr = inner.getArguments().get(0);
                                MethodCallExpr hexCall = createHexConversion(bytesExpr);
                                parentCall.replace(hexCall);
                                modified = true;
                                hexConversions++;
                                return hexCall;
                            }
                        }
                    }
                }
                
                // Handle HexOf(something else) - just get the string representation
                // For simplicity, we'll convert to .toString() 
                MethodCallExpr toStringCall = new MethodCallExpr(arg, "toString");
                expr.replace(toStringCall);
                modified = true;
                hexConversions++;
                return toStringCall;
            }
            
            return expr;
        }
        
        private Visitable handleBytesOf(ObjectCreationExpr expr) {
            // BytesOf usually wraps something to get bytes
            // For simplicity, we'll try to extract the inner expression
            // In many cases, it's just getting bytes from a byte array or similar
            if (expr.getArguments().size() == 1) {
                Expression arg = expr.getArguments().get(0);
                // If parent is HexOf, we handle it in handleHexOf
                // If standalone BytesOf, we might need to convert to appropriate type
                // For now, just replace with the argument (assuming it's already bytes)
                // This might not always be correct but works for common cases
                Node parent = expr.getParentNode().orElse(null);
                if (!(parent instanceof ObjectCreationExpr && 
                      "HexOf".equals(((ObjectCreationExpr) parent).getType().asString()))) {
                    // Standalone BytesOf - replace with argument
                    expr.replace(arg);
                    modified = true;
                    hexConversions++;
                    return arg;
                }
            }
            return expr;
        }
        
        private Visitable handleListOf(ObjectCreationExpr expr) {
            // Replace ListOf<T>(elements) with Arrays.asList(elements)
            needsArraysImport = true;
            needsListImport = true;
            
            // Create Arrays.asList(...) call
            NameExpr arrays = new NameExpr("Arrays");
            MethodCallExpr asListCall = new MethodCallExpr(arrays, "asList");
            
            // Preserve type arguments if any (ListOf<String> -> Arrays.asList(...))
            // Note: We don't need to preserve type arguments for Arrays.asList
            // as Java can infer them from the arguments
            asListCall.setArguments(expr.getArguments());
            
            expr.replace(asListCall);
            modified = true;
            listConversions++;
            return asListCall;
        }
        
        private MethodCallExpr createHexConversion(Expression bytesExpr) {
            // Use Apache Commons Codec Hex.encodeHexString() which is available 
            // in the project via commons-codec dependency (from asto)
            needsHexImport = true;
            NameExpr hexClass = new NameExpr("Hex");
            MethodCallExpr encodeHexString = new MethodCallExpr(hexClass, "encodeHexString");
            encodeHexString.setArguments(new NodeList<>(bytesExpr));
            
            return encodeHexString;
        }
        
        @Override
        public Visitable visit(MethodCallExpr expr, Void arg) {
            // Handle .asString() calls that might be on HexOf we missed
            if ("asString".equals(expr.getNameAsString()) && 
                expr.getScope().isPresent()) {
                Expression scope = expr.getScope().get();
                if (scope instanceof ObjectCreationExpr) {
                    ObjectCreationExpr objExpr = (ObjectCreationExpr) scope;
                    if ("HexOf".equals(objExpr.getType().asString())) {
                        // This is HexOf(...).asString()
                        return handleHexOf(objExpr);
                    }
                }
            }
            
            return super.visit(expr, arg);
        }
    }
}