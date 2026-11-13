package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Path.of(args[0]);
        System.out.println("Transforming files in: " + sourceDir);
        
        JavaParser parser = new JavaParser();
        
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> transformFile(p, parser));
    }
    
    private static void transformFile(Path filePath, JavaParser parser) {
        try {
            Optional<CompilationUnit> cuOpt = parser.parse(filePath).getResult();
            
            if (cuOpt.isPresent()) {
                CompilationUnit cu = cuOpt.get();
                AtomicBoolean modified = new AtomicBoolean(false);
                AtomicBoolean needsHexImport = new AtomicBoolean(false);
                
                // Remove org.cactoos imports
                boolean importsRemoved = cu.getImports().removeIf(imp -> 
                    imp.getNameAsString().startsWith("org.cactoos.")
                );
                modified.set(importsRemoved);
                
                // Create visitor with parser reference
                cu.accept(new ModifierVisitor<Void>() {
                    private JavaParser parserRef = parser;
                    
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        // Check for .asString() call
                        if (n.getNameAsString().equals("asString")) {
                            Optional<Expression> scope = n.getScope();
                            if (scope.isPresent() && scope.get() instanceof ObjectCreationExpr) {
                                ObjectCreationExpr hexOfCreation = (ObjectCreationExpr) scope.get();
                                String typeName = hexOfCreation.getType().toString();
                                if (typeName.equals("HexOf") || typeName.startsWith("HexOf<")) {
                                    // Check if HexOf constructor has BytesOf argument
                                    if (!hexOfCreation.getArguments().isEmpty()) {
                                        Expression argExpr = hexOfCreation.getArguments().get(0);
                                        if (argExpr instanceof ObjectCreationExpr) {
                                            ObjectCreationExpr bytesOfCreation = (ObjectCreationExpr) argExpr;
                                            String bytesOfTypeName = bytesOfCreation.getType().toString();
                                            if (bytesOfTypeName.equals("BytesOf") || bytesOfTypeName.startsWith("BytesOf<")) {
                                                if (!bytesOfCreation.getArguments().isEmpty()) {
                                                    // Extract the byte array expression
                                                    Expression byteArrayExpr = bytesOfCreation.getArguments().get(0);
                                                    // Replace with hex conversion
                                                    needsHexImport.set(true);
                                                    modified.set(true);
                                                    String replacement = createHexConversion(byteArrayExpr.toString());
                                                    return parserRef.parseExpression(replacement).getResult().orElse(n);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return super.visit(n, arg);
                    }
                    
                    @Override
                    public Visitable visit(ObjectCreationExpr n, Void arg) {
                        // Transform ListOf creations
                        String typeName = n.getType().toString();
                        if (typeName.equals("ListOf") || typeName.startsWith("ListOf<")) {
                            modified.set(true);
                            String typeArgs = "";
                            if (n.getType().getTypeArguments().isPresent()) {
                                typeArgs = n.getType().getTypeArguments().get().toString();
                            }
                            StringBuilder argsBuilder = new StringBuilder();
                            for (int i = 0; i < n.getArguments().size(); i++) {
                                if (i > 0) argsBuilder.append(", ");
                                argsBuilder.append(n.getArguments().get(i).toString());
                            }
                            String replacement;
                            if (typeArgs.isEmpty()) {
                                replacement = String.format("java.util.Arrays.asList(%s)", argsBuilder.toString());
                            } else {
                                replacement = String.format("java.util.Arrays.<%s>asList(%s)", typeArgs, argsBuilder.toString());
                            }
                            return parserRef.parseExpression(replacement).getResult().orElse(n);
                        }
                        return super.visit(n, arg);
                    }
                }, null);
                
                // Add commons-codec import if needed
                if (needsHexImport.get()) {
                    boolean hasHexImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals("org.apache.commons.codec.binary.Hex"));
                    if (!hasHexImport) {
                        cu.addImport("org.apache.commons.codec.binary.Hex");
                        modified.set(true);
                    }
                }
                
                if (modified.get()) {
                    // Write back the transformed file
                    Files.writeString(filePath, cu.toString());
                    System.out.println("Transformed: " + filePath);
                }
            }
        } catch (Exception e) {
            System.err.println("Error transforming " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String createHexConversion(String byteArrayExpr) {
        // Use commons-codec Hex.encodeHexString which is available in the project
        return String.format(
            "Hex.encodeHexString(%s).toLowerCase()", 
            byteArrayExpr
        );
    }
}