package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int transformed = 0;
        
        for (Path file : javaFiles) {
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            if (cu == null) continue;
            
            boolean[] modified = new boolean[]{false};
            boolean[] needsDigestUtils = new boolean[]{false};
            boolean[] needsArrays = new boolean[]{false};
            boolean[] needsHex = new boolean[]{false};
            
            // First, check if we need to add DigestUtils import
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(MethodCallExpr n, Void arg) {
                    if (n.getNameAsString().equals("asString")) {
                        Optional<Expression> scope = n.getScope();
                        if (scope.isPresent() && scope.get() instanceof ObjectCreationExpr) {
                            ObjectCreationExpr creation = (ObjectCreationExpr) scope.get();
                            if (creation.getType().asString().equals("HexOf")) {
                                Optional<Expression> hexArg = creation.getArguments().stream()
                                    .findFirst();
                                if (hexArg.isPresent() && hexArg.get() instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr bytesCreation = (ObjectCreationExpr) hexArg.get();
                                    if (bytesCreation.getType().asString().equals("BytesOf")) {
                                        needsDigestUtils[0] = true;
                                    }
                                }
                            }
                        }
                    }
                    return (Node) super.visit(n, arg);
                }
            }, null);
            
            // Transform the code
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(ImportDeclaration n, Void arg) {
                    // Remove all cactoos imports
                    String importName = n.getNameAsString();
                    if (importName.startsWith("org.cactoos")) {
                        modified[0] = true;
                        return null; // Remove import
                    }
                    return (Node) super.visit(n, arg);
                }
                
                @Override
                public Node visit(ObjectCreationExpr n, Void arg) {
                    // Check for pattern: new Digest.Sha256(new HexOf(new BytesOf(...)).asString())
                    if (n.getType().asString().endsWith("Digest.Sha256")) {
                        NodeList<Expression> args = n.getArguments();
                        if (args.size() == 1) {
                            Expression exprArg = args.get(0);
                            Expression transformed = transformHexOfBytesOfChain(exprArg);
                            if (transformed != exprArg) {
                                args.set(0, transformed);
                                modified[0] = true;
                            }
                        }
                    }
                    
                    // Handle ListOf replacement
                    String typeName = n.getType().asString();
                    if (typeName.startsWith("ListOf")) {
                        // Replace new ListOf<T>(...) with Arrays.asList(...)
                        MethodCallExpr replacement = new MethodCallExpr(
                            new NameExpr("Arrays"),
                            "asList",
                            n.getArguments()
                        );
                        modified[0] = true;
                        needsArrays[0] = true;
                        return replacement;
                    }
                    
                    // Also handle standalone new HexOf(...) or new BytesOf(...)
                    if (n.getType().asString().equals("HexOf") || 
                        n.getType().asString().equals("BytesOf")) {
                        // These will be handled by the method call visitor
                    }
                    
                    return (Node) super.visit(n, arg);
                }
                
                @Override
                public Node visit(MethodCallExpr n, Void arg) {
                    // Check for pattern: new HexOf(new BytesOf(...)).asString()
                    if (n.getNameAsString().equals("asString")) {
                        Optional<Expression> scope = n.getScope();
                        if (scope.isPresent() && scope.get() instanceof ObjectCreationExpr) {
                            ObjectCreationExpr creation = (ObjectCreationExpr) scope.get();
                            if (creation.getType().asString().equals("HexOf")) {
                                NodeList<Expression> hexArgs = creation.getArguments();
                                if (hexArgs.size() == 1) {
                                    Expression hexArg = hexArgs.get(0);
                                    if (hexArg instanceof ObjectCreationExpr) {
                                        ObjectCreationExpr bytesCreation = (ObjectCreationExpr) hexArg;
                                        if (bytesCreation.getType().asString().equals("BytesOf")) {
                                            NodeList<Expression> bytesArgs = bytesCreation.getArguments();
                                            if (bytesArgs.size() == 1) {
                                                // Replace with Hex.encodeHexString(...)
                                                MethodCallExpr replacement = new MethodCallExpr(
                                                    new NameExpr("Hex"),
                                                    "encodeHexString",
                                                    new NodeList<>(bytesArgs.get(0))
                                                );
                                                modified[0] = true;
                                                needsHex[0] = true;
                                                return replacement;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Also check for DigestUtils.sha256Hex calls that might already exist
                    if (n.getNameAsString().equals("sha256Hex")) {
                        Optional<Expression> scope = n.getScope();
                        if (scope.isPresent() && scope.get().toString().equals("DigestUtils")) {
                            needsDigestUtils[0] = true;
                        }
                    }
                    
                    // Check for Arrays.asList calls
                    if (n.getNameAsString().equals("asList")) {
                        Optional<Expression> scope = n.getScope();
                        if (scope.isPresent() && scope.get().toString().equals("Arrays")) {
                            needsArrays[0] = true;
                        }
                    }
                    
                    // Check for Hex.encodeHexString calls
                    if (n.getNameAsString().equals("encodeHexString")) {
                        Optional<Expression> scope = n.getScope();
                        if (scope.isPresent() && scope.get().toString().equals("Hex")) {
                            needsHex[0] = true;
                        }
                    }
                    
                    return (Node) super.visit(n, arg);
                }
                
                private Expression transformHexOfBytesOfChain(Expression expr) {
                    // Handle pattern: new HexOf(new BytesOf(...)).asString()
                    if (expr instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) expr;
                        if (methodCall.getNameAsString().equals("asString")) {
                            Optional<Expression> scope = methodCall.getScope();
                            if (scope.isPresent() && scope.get() instanceof ObjectCreationExpr) {
                                ObjectCreationExpr hexOf = (ObjectCreationExpr) scope.get();
                                if (hexOf.getType().asString().equals("HexOf")) {
                                    NodeList<Expression> hexArgs = hexOf.getArguments();
                                    if (hexArgs.size() == 1) {
                                        Expression hexArg = hexArgs.get(0);
                                        if (hexArg instanceof ObjectCreationExpr) {
                                            ObjectCreationExpr bytesOf = (ObjectCreationExpr) hexArg;
                                            if (bytesOf.getType().asString().equals("BytesOf")) {
                                                NodeList<Expression> bytesArgs = bytesOf.getArguments();
                                                if (bytesArgs.size() == 1) {
                                                    // Convert bytes to hex using Hex.encodeHexString
                                                    MethodCallExpr hexConversion = new MethodCallExpr(
                                                        new NameExpr("Hex"),
                                                        "encodeHexString",
                                                        new NodeList<>(bytesArgs.get(0))
                                                    );
                                                    needsHex[0] = true;
                                                    return hexConversion;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return expr;
                }
            }, null);
            
            // Add DigestUtils import if needed
            if (needsDigestUtils[0]) {
                boolean hasDigestUtils = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("org.apache.commons.codec.digest.DigestUtils"));
                if (!hasDigestUtils) {
                    cu.addImport("org.apache.commons.codec.digest.DigestUtils");
                    modified[0] = true;
                }
            }
            
            // Add Arrays import if needed
            if (needsArrays[0]) {
                boolean hasArrays = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("java.util.Arrays"));
                if (!hasArrays) {
                    cu.addImport("java.util.Arrays");
                    modified[0] = true;
                }
            }
            
            // Add Hex import if needed
            if (needsHex[0]) {
                boolean hasHex = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("org.apache.commons.codec.binary.Hex"));
                if (!hasHex) {
                    cu.addImport("org.apache.commons.codec.binary.Hex");
                    modified[0] = true;
                }
            }
            
            if (modified[0]) {
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
                String transformedCode = printer.print(cu);
                Files.write(file, transformedCode.getBytes());
                transformed++;
                System.out.println("Transformed: " + file);
            }
        }
        
        System.out.println("Transformed " + transformed + " files");
    }
}