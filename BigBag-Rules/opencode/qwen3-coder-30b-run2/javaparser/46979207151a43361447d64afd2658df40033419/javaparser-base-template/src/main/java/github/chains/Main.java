package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.Statement;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the project
        String projectPath = args.length > 0 ? args[0] : "/workspace/billy";
        Path rootPath = Paths.get(projectPath);
        
        try (Stream<Path> paths = Files.walk(rootPath)) {
            List<Path> javaFiles = paths
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
                
            for (Path javaFilePath : javaFiles) {
                processJavaFile(javaFilePath);
            }
        }
    }
    
    private static void processJavaFile(Path javaFilePath) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(javaFilePath.toFile()));
        
        // Apply the transformation to fix JAXBToStringStrategy.getInstance() calls
        new JAXBToStringStrategyVisitor().visit(cu, null);
        
        // Save the modified file
        try (FileWriter writer = new FileWriter(javaFilePath.toFile())) {
            writer.write(cu.toString());
        }
    }
    
    /**
     * Visitor that finds and fixes calls to JAXBToStringStrategy.getInstance() 
     * that should use JAXBToStringStrategy.INSTANCE instead
     */
    private static class JAXBToStringStrategyVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for calls to getInstance() on JAXBToStringStrategy
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                if (scope instanceof FieldAccessExpr) {
                    FieldAccessExpr fieldAccess = (FieldAccessExpr) scope;
                    if (fieldAccess.getNameAsString().equals("JAXBToStringStrategy")) {
                        // Check if the method called is getInstance()
                        if (methodCall.getNameAsString().equals("getInstance")) {
                            // Replace with INSTANCE field access
                            Expression replacement = new FieldAccessExpr(
                                new NameExpr("JAXBToStringStrategy"), 
                                "INSTANCE"
                            );
                            methodCall.replace(replacement);
                        }
                    }
                } else if (scope instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope;
                    if (nameExpr.getNameAsString().equals("JAXBToStringStrategy")) {
                        // Check if the method called is getInstance()
                        if (methodCall.getNameAsString().equals("getInstance")) {
                            // Replace with INSTANCE field access
                            Expression replacement = new FieldAccessExpr(
                                new NameExpr("JAXBToStringStrategy"), 
                                "INSTANCE"
                            );
                            methodCall.replace(replacement);
                        }
                    }
                }
            }
        }
    }
}
