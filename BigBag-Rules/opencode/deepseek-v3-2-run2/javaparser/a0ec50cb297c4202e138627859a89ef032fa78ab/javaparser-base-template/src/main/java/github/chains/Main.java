package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            JavaParser parser = new JavaParser();
            int modifiedFiles = 0;
            
            for (File file : javaFiles) {
                if (processFile(parser, file)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean processFile(JavaParser parser, File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + file);
            return false;
        }
        
        boolean[] modified = new boolean[]{false};
        
        // Visitor to handle HexOf/BytesOf replacement
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ObjectCreationExpr n, Void arg) {
                // Check for new HexOf(new BytesOf(expr)).asString()
                if (n.getType().asString().equals("HexOf")) {
                    NodeList<Expression> args = n.getArguments();
                    if (args.size() == 1) {
                        Expression argExpr = args.get(0);
                        if (argExpr instanceof ObjectCreationExpr) {
                            ObjectCreationExpr inner = (ObjectCreationExpr) argExpr;
                            if (inner.getType().asString().equals("BytesOf") && 
                                inner.getArguments().size() == 1) {
                                
                                // Get the parent method call if it's .asString()
                                if (n.getParentNode().isPresent() && 
                                    n.getParentNode().get() instanceof MethodCallExpr) {
                                    MethodCallExpr methodCall = (MethodCallExpr) n.getParentNode().get();
                                    if (methodCall.getName().asString().equals("asString")) {
                                        // Replace with Hex.encodeHexString(expr)
                                        Expression bytesExpr = inner.getArguments().get(0);
                                        MethodCallExpr hexCall = new MethodCallExpr(
                                            new NameExpr("Hex"),
                                            "encodeHexString",
                                            new NodeList<>(bytesExpr)
                                        );
                                        
                                        // Replace the method call with hex call
                                        if (methodCall.replace(hexCall)) {
                                            modified[0] = true;
                                            
                                            // Add Hex import if not present
                                            boolean hasHexImport = cu.getImports().stream()
                                                .anyMatch(imp -> imp.getNameAsString().equals("org.apache.commons.codec.binary.Hex"));
                                            if (!hasHexImport) {
                                                cu.addImport("org.apache.commons.codec.binary.Hex");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Check for new ListOf<T>(...)
                String typeName = n.getType().asString();
                // Handle cases like "ListOf<Matcher<? super Response>>" 
                // by checking if it starts with "ListOf"
                if (typeName.startsWith("ListOf")) {
                    // Replace with Arrays.asList(...)
                    MethodCallExpr arraysAsList = new MethodCallExpr(
                        new NameExpr("Arrays"),
                        "asList",
                        n.getArguments()
                    );
                    
                    if (n.replace(arraysAsList)) {
                        modified[0] = true;
                        
                        // Add Arrays import if not present
                        boolean hasArraysImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("java.util.Arrays"));
                        if (!hasArraysImport) {
                            cu.addImport("java.util.Arrays");
                        }
                    }
                }
                
                return super.visit(n, arg);
            }
            
            @Override
            public Node visit(ImportDeclaration n, Void arg) {
                String importName = n.getNameAsString();
                
                // Remove cactoos imports
                if (importName.startsWith("org.cactoos.text.HexOf") ||
                    importName.startsWith("org.cactoos.io.BytesOf") ||
                    importName.startsWith("org.cactoos.list.ListOf")) {
                    
                    modified[0] = true;
                    return null; // Remove import
                }
                
                return super.visit(n, arg);
            }
        }, null);
        
        if (modified[0]) {
            // Write back the modified file
            Files.write(file.toPath(), cu.toString().getBytes());
            System.out.println("Modified: " + file);
            return true;
        }
        
        return false;
    }
}