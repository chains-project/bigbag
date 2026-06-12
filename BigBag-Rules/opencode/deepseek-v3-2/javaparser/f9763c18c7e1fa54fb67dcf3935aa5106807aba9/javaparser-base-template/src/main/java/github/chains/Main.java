package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            javaFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Warning: Could not parse " + javaFile);
                continue;
            }
            
            boolean hasHtmlUnitScriptResultImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult"));
            
            if (hasHtmlUnitScriptResultImport) {
                System.out.println("Processing: " + javaFile);
                
                // Remove the import
                NodeList<ImportDeclaration> newImports = new NodeList<>();
                for (ImportDeclaration imp : cu.getImports()) {
                    if (!imp.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        newImports.add(imp);
                    }
                }
                cu.setImports(newImports);
                
                // Apply transformation to remove ScriptResult wrapper
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(ObjectCreationExpr n, Void arg) {
                        // Check if this is new ScriptResult(...)
                        if (n.getType().asString().equals("ScriptResult")) {
                            // Get the argument passed to constructor
                            if (n.getArguments().size() == 1) {
                                Expression argExpr = n.getArguments().get(0);
                                
                                // Check if parent is a MethodCallExpr for getJavaScriptResult()
                                if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof MethodCallExpr) {
                                    MethodCallExpr parentCall = (MethodCallExpr) n.getParentNode().get();
                                    if (parentCall.getNameAsString().equals("getJavaScriptResult")) {
                                        // This is pattern: new ScriptResult(x).getJavaScriptResult()
                                        // Replace entire parent expression with just x
                                        return argExpr.clone();
                                    }
                                }
                                
                                // Otherwise, just replace new ScriptResult(x) with x
                                return argExpr.clone();
                            }
                        }
                        return super.visit(n, arg);
                    }
                    
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        // Check if this is .getJavaScriptResult()
                        if (n.getNameAsString().equals("getJavaScriptResult")) {
                            // Handle different patterns:
                            
                            // Pattern 1: scriptResult.getJavaScriptResult() -> scriptResult
                            // Pattern 2: new ScriptResult(x).getJavaScriptResult() -> handled in visit(ObjectCreationExpr)
                            // Pattern 3: result.getJavaScriptResult() -> result (where result is the Object from executeScript)
                            
                            if (n.getScope().isPresent()) {
                                return n.getScope().get().clone();
                            }
                        }
                        return super.visit(n, arg);
                    }
                    
                    @Override
                    public Visitable visit(ClassOrInterfaceType n, Void arg) {
                        // Handle type references like "ScriptResult" in variable declarations
                        if (n.getNameAsString().equals("ScriptResult")) {
                            // Replace ScriptResult type with Object since we're removing the wrapper
                            return new ClassOrInterfaceType("Object");
                        }
                        return super.visit(n, arg);
                    }
                }, null);
                
                // Write the transformed file back
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                String transformedCode = cu.toString(config);
                Files.write(javaFile, transformedCode.getBytes());
                System.out.println("  Updated: " + javaFile);
            }
        }
        
        System.out.println("Transformation completed.");
    }
}