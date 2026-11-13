package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws Exception {
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .toList();
            
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            
            CompilationUnit cu;
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                cu = parser.parse(in).getResult().orElseThrow();
            }
            
            JAXBToStringStrategyFixVisitor visitor = new JAXBToStringStrategyFixVisitor();
            visitor.visit(cu, null);
            
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(cu.toString());
            }
        }
    }
    
    private static class JAXBToStringStrategyFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a call to getInstance()
            if (n.getNameAsString().equals("getInstance")) {
                // Check if the scope is JAXBToStringStrategy (either simple name or fully qualified)
                if (n.getScope().isPresent()) {
                    String scopeStr = n.getScope().get().toString();
                    
                    // Check for simple class name or fully qualified name
                    boolean isJAXBToStringStrategy = false;
                    
                    if (scopeStr.equals("JAXBToStringStrategy")) {
                        isJAXBToStringStrategy = true;
                    } else if (scopeStr.equals("org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy")) {
                        isJAXBToStringStrategy = true;
                    } else if (scopeStr.endsWith(".JAXBToStringStrategy")) {
                        // Also handle any possible import aliasing
                        isJAXBToStringStrategy = true;
                    }
                    
                    if (isJAXBToStringStrategy) {
                        // Replace method call with field access
                        FieldAccessExpr fieldAccess = new FieldAccessExpr(
                            n.getScope().get(), 
                            "INSTANCE"
                        );
                        n.replace(fieldAccess);
                        
                        System.out.println("  Fixed: " + n.getRange().map(r -> r.begin.line + ":" + r.begin.column).orElse("?"));
                    }
                }
            }
        }
    }
}