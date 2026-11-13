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
import com.github.javaparser.printer.DefaultPrettyPrinter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Hamcrest API usage in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        JavaParser parser = new JavaParser();
        int transformed = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) continue;
                
                HamcrestApiTransformer transformer = new HamcrestApiTransformer();
                cu.accept(transformer, null);
                
                if (transformer.modified) {
                    // Ensure CoreMatchers import is present
                    boolean hasCoreMatchersImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals("org.hamcrest.CoreMatchers"));
                    
                    if (!hasCoreMatchersImport) {
                        cu.addImport("org.hamcrest.CoreMatchers");
                    }
                    
                    // Write back the transformed file
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                    String transformedCode = printer.print(cu);
                    
                    try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                        writer.write(transformedCode);
                    }
                    
                    transformed++;
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Transformation complete. Files modified: " + transformed);
    }
    
    private static class HamcrestApiTransformer extends ModifierVisitor<Void> {
        boolean modified = false;
        
        @Override
        public com.github.javaparser.ast.visitor.Visitable visit(ObjectCreationExpr n, Void arg) {
            // Visit children first
            super.visit(n, arg);
            
            String typeName = n.getType().asString();
            String baseTypeName = typeName.replaceAll("<.*>", "");
            
            // Check if this is a Hamcrest matcher constructor
            if (baseTypeName.equals("StringContains") || baseTypeName.equals("StringStartsWith") ||
                baseTypeName.equals("IsEqual") || baseTypeName.equals("IsNot") ||
                baseTypeName.equals("IsInstanceOf") || baseTypeName.equals("AllOf") ||
                baseTypeName.equals("AnyOf")) {
                
                System.out.println("DEBUG: Transforming " + typeName + " -> " + baseTypeName);
                
                NodeList<Expression> args = n.getArguments();
                String methodName = getCoreMatchersMethodName(baseTypeName);
                
                if (methodName != null && !args.isEmpty()) {
                    MethodCallExpr newExpr = new MethodCallExpr(new NameExpr("CoreMatchers"), methodName);
                    for (Expression argument : args) {
                        newExpr.addArgument(argument);
                    }
                    modified = true;
                    return newExpr;
                }
            }
            
            return n;
        }
        
        private String getCoreMatchersMethodName(String baseTypeName) {
            switch (baseTypeName) {
                case "StringContains": return "containsString";
                case "StringStartsWith": return "startsWith";
                case "IsEqual": return "equalTo";
                case "IsNot": return "not";
                case "IsInstanceOf": return "instanceOf";
                case "AllOf": return "allOf";
                case "AnyOf": return "anyOf";
                default: return null;
            }
        }
    }
}