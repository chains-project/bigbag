package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Main {
    
    private static final Map<String, String> ERROR_ATTRIBUTE_MAPPINGS = new HashMap<>();
    
    static {
        ERROR_ATTRIBUTE_MAPPINGS.put("javax.servlet.error.exception", "jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION");
        ERROR_ATTRIBUTE_MAPPINGS.put("javax.servlet.error.exception_type", "jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION_TYPE");
        ERROR_ATTRIBUTE_MAPPINGS.put("javax.servlet.error.message", "jakarta.servlet.RequestDispatcher.ERROR_MESSAGE");
        ERROR_ATTRIBUTE_MAPPINGS.put("javax.servlet.error.request_uri", "jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI");
        ERROR_ATTRIBUTE_MAPPINGS.put("javax.servlet.error.status_code", "jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE");
        ERROR_ATTRIBUTE_MAPPINGS.put("javax.servlet.error.servlet_name", "jakarta.servlet.RequestDispatcher.ERROR_SERVLET_NAME");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);
        
        try {
            processDirectory(Paths.get(sourceDirectory));
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(content);
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                boolean modified = cu.accept(new ErrorAttributeTransformer(), null) != null;
                
                if (modified) {
                    String transformedContent = cu.toString();
                    Files.writeString(filePath, transformedContent);
                    System.out.println("Transformed: " + filePath);
                }
            } else {
                System.err.println("Failed to parse file: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ErrorAttributeTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(StringLiteralExpr n, Void arg) {
            String value = n.getValue();
            
            if (ERROR_ATTRIBUTE_MAPPINGS.containsKey(value)) {
                String replacement = ERROR_ATTRIBUTE_MAPPINGS.get(value);
                return parseFieldAccessExpression(replacement);
            }
            
            return super.visit(n, arg);
        }
        
        private FieldAccessExpr parseFieldAccessExpression(String expression) {
            String[] parts = expression.split("\\.");
            
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid field access expression: " + expression);
            }
            
            NameExpr scope = new NameExpr(parts[0] + "." + parts[1]);
            FieldAccessExpr fieldAccess = new FieldAccessExpr(scope, parts[2]);
            
            return fieldAccess;
        }
    }
}