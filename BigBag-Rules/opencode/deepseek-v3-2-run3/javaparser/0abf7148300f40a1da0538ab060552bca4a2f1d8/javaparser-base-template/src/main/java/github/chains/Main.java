package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    // Configuration: map of method names to wrapper types needed
    private static final Map<String, String> METHOD_WRAPPERS = new HashMap<>();
    
    static {
        // JasperReports 6.19.1 breaking changes: primitive to boxed type
        METHOD_WRAPPERS.put("setLineWidth", "Float");
        METHOD_WRAPPERS.put("setFontSize", "Float");
        METHOD_WRAPPERS.put("setLineSpacingSize", "Float");
        METHOD_WRAPPERS.put("setBackgroundAlpha", "Float");
        METHOD_WRAPPERS.put("setForegroundAlpha", "Float");
        METHOD_WRAPPERS.put("setAxisLineWidth", "Float");
        METHOD_WRAPPERS.put("setFloatFontSize", "Float");
        METHOD_WRAPPERS.put("setMinPercentage", "Float");
        // Add other methods as needed
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <sourceDir>");
            System.err.println("Example: java Main /path/to/src");
            System.err.println("\nThis transformation handles JasperReports 6.19.1 breaking changes:");
            System.err.println("Methods that changed from primitive to boxed types:");
            for (Map.Entry<String, String> entry : METHOD_WRAPPERS.entrySet()) {
                System.err.println("  " + entry.getKey() + " -> " + entry.getValue() + ".valueOf()");
            }
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        try {
            transformProject(sourceDir);
        } catch (IOException e) {
            System.err.println("Error transforming project: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                MethodCallVisitor visitor = new MethodCallVisitor();
                visitor.visit(cu, null);
                
                if (visitor.isModified()) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    transformedCalls += visitor.getTransformCount();
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformedCalls + " method calls in " + transformedFiles + " files.");
    }
    
    private static class MethodCallVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        private int transformCount = 0;
        
        public boolean isModified() {
            return modified;
        }
        
        public int getTransformCount() {
            return transformCount;
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            String methodName = n.getNameAsString();
            if (METHOD_WRAPPERS.containsKey(methodName)) {
                String wrapperType = METHOD_WRAPPERS.get(methodName);
                
                if (n.getArguments().size() == 1) {
                    Expression argExpr = n.getArgument(0);
                    String argStr = argExpr.toString();
                    
                    // Check if argument needs boxing
                    if (needsBoxing(argStr, wrapperType)) {
                        // Create wrapperType.valueOf() wrapper
                        MethodCallExpr valueOfCall = new MethodCallExpr(wrapperType + ".valueOf", argExpr.clone());
                        
                        // Replace the argument with wrapperType.valueOf(arg)
                        n.getArguments().set(0, valueOfCall);
                        
                        modified = true;
                        transformCount++;
                        System.out.println("Transformed: " + methodName + " with " + wrapperType + ".valueOf()");
                    }
                }
            }
        }
        
        private boolean needsBoxing(String argStr, String wrapperType) {
            // Skip if already wrapped
            if (argStr.contains(wrapperType + ".valueOf") || 
                argStr.contains("new " + wrapperType) ||
                argStr.contains("(" + wrapperType + ")") ||
                argStr.contains(".floatValue()") ||
                argStr.contains(".doubleValue()") ||
                argStr.contains(".intValue()")) {
                return false;
            }
            
            // Check for common primitive patterns
            // This is a heuristic - in production would need type inference
            return true;
        }
    }
}