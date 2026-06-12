package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for API breaking changes in com.artipie.http library.
 * 
 * This transformation handles the case where methods/constructors that previously
 * accepted individual Header objects or varargs of Map.Entry<String, String> now
 * require Headers container or Iterable<Map.Entry<String, String>>.
 * 
 * Transformation pattern:
 * - Old: SomeClass.method(header1, header2, ...)
 * - New: SomeClass.method(new Headers.From(header1, header2, ...))
 * 
 * This is a generic, reusable transformation that can be applied to any project
 * affected by similar API breaking changes in dependency libraries.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = findJavaFiles(new File(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformed = 0;
            for (File file : javaFiles) {
                if (transformFile(file)) {
                    transformed++;
                }
            }
            
            System.out.println("Transformed " + transformed + " files");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> result = new ArrayList<>();
        findJavaFilesRecursive(dir, result);
        return result;
    }
    
    private static void findJavaFilesRecursive(File dir, List<File> result) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        findJavaFilesRecursive(file, result);
                    } else if (file.getName().endsWith(".java")) {
                        result.add(file);
                    }
                }
            }
        }
    }
    
    private static boolean transformFile(File file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + file)
        );
        
        boolean[] modified = new boolean[1];
        
        // Check if we need to add Headers import
        boolean hasHeadersImport = cu.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("com.artipie.http.Headers"));
        
        // Visitor to transform API breaking changes
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ObjectCreationExpr expr, Void arg) {
                // Check for constructor patterns that might need transformation
                String typeName = expr.getType().asString();
                
                // Common response constructors that take headers
                if (typeName.equals("RsWithHeaders") || typeName.equals("RsWithBody") || 
                    typeName.equals("RsWithStatus") || typeName.equals("RsFull")) {
                    
                    // These constructors typically take a Response followed by headers
                    // We need to check if header arguments need wrapping
                    if (expr.getArguments().size() >= 2) {
                        // Heuristic: check if arguments after first look like header objects
                        boolean needsTransformation = false;
                        for (int i = 1; i < expr.getArguments().size(); i++) {
                            String argStr = expr.getArgument(i).toString();
                            // Check for common header patterns
                            if (argStr.contains("new ") && 
                                (argStr.contains("Header") || 
                                 argStr.contains("ContentType") ||
                                 argStr.contains("ContentLength") ||
                                 argStr.contains("Location") ||
                                 argStr.contains("Authorization") ||
                                 argStr.contains("DigestHeader") ||
                                 argStr.contains("JsonContentType") ||
                                 argStr.contains("WwwAuthenticate"))) {
                                needsTransformation = true;
                                break;
                            }
                        }
                        
                        if (needsTransformation) {
                            System.out.println("Transforming " + typeName + " constructor in " + file);
                            
                            // Build new arguments: keep first (Response), wrap rest in Headers.From
                            List<Expression> newArgs = new ArrayList<>();
                            newArgs.add(expr.getArgument(0));
                            
                            StringBuilder headersBuilder = new StringBuilder();
                            for (int i = 1; i < expr.getArguments().size(); i++) {
                                if (i > 1) headersBuilder.append(", ");
                                headersBuilder.append(expr.getArgument(i).toString());
                            }
                            
                            Expression headersFrom = parseExpression("new Headers.From(" + headersBuilder.toString() + ")");
                            newArgs.add(headersFrom);
                            
                            expr.getArguments().clear();
                            expr.getArguments().addAll(newArgs);
                            modified[0] = true;
                            
                            if (!hasHeadersImport) {
                                cu.addImport("com.artipie.http.Headers");
                                modified[0] = true;
                            }
                        }
                    }
                }
                
                return super.visit(expr, arg);
            }
            
            @Override
            public Visitable visit(MethodCallExpr expr, Void arg) {
                // Could extend to handle method calls if needed
                return super.visit(expr, arg);
            }
        }, null);
        
        if (modified[0]) {
            // Write back the transformed file
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String transformedCode = printer.print(cu);
            
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(transformedCode);
            }
            
            System.out.println("Successfully transformed " + file);
        }
        
        return modified[0];
    }
    
    private static Expression parseExpression(String exprString) {
        JavaParser parser = new JavaParser();
        return parser.parseExpression(exprString).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse expression: " + exprString)
        );
    }
}