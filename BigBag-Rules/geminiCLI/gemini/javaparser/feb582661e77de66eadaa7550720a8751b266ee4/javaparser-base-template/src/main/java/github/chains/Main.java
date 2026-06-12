package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path startPath = Paths.get("/workspace/liquibase-mssql/src/java");

        try (Stream<Path> paths = Files.walk(startPath)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path path : javaFiles) {
                boolean changed = false;
                CompilationUnit cu = StaticJavaParser.parse(path);

                // Change imports
                for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
                    if (id.getNameAsString().equals("liquibase.util.StringUtils")) {
                        id.setName("liquibase.util.StringUtil");
                        changed = true;
                    }
                }

                // Change StringUtils to StringUtil in expressions
                for (NameExpr ne : cu.findAll(NameExpr.class)) {
                    if (ne.getNameAsString().equals("StringUtils")) {
                        ne.setName("StringUtil");
                        changed = true;
                    }
                }
                
                // We need to find ExecutorService.getInstance()
                for (MethodCallExpr mce : cu.findAll(MethodCallExpr.class)) {
                    if (mce.getNameAsString().equals("getInstance") 
                            && mce.getScope().isPresent() 
                            && mce.getScope().get().toString().equals("ExecutorService")) {
                        
                        // Replace ExecutorService.getInstance() with Scope.getCurrentScope().getSingleton(ExecutorService.class)
                        mce.setName("getSingleton");
                        mce.setScope(StaticJavaParser.parseExpression("liquibase.Scope.getCurrentScope()"));
                        mce.setArguments(new com.github.javaparser.ast.NodeList<>());
                        mce.addArgument(StaticJavaParser.parseExpression("ExecutorService.class"));
                        
                        // Ensure Scope is imported or use fully qualified name
                        // Here we just use fully qualified liquibase.Scope which we put in the scope directly!
                        
                        // Also, the following call should be getExecutor("jdbc", this)
                        if (mce.getParentNode().isPresent() && mce.getParentNode().get() instanceof MethodCallExpr) {
                            MethodCallExpr parent = (MethodCallExpr) mce.getParentNode().get();
                            if (parent.getNameAsString().equals("getExecutor") && parent.getArguments().size() == 1) {
                                parent.getArguments().add(0, new StringLiteralExpr("jdbc"));
                            }
                        }
                        
                        changed = true;
                    }
                }

                if (changed) {
                    Files.write(path, cu.toString().getBytes());
                    System.out.println("Modified " + path);
                }
            }
        }
    }
}
