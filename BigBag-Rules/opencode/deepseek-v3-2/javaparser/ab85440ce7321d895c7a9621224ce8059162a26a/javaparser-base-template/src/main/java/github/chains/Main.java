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
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            javaFiles = walk
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int transformed = 0;
        
        for (Path javaFile : javaFiles) {
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                continue;
            }
            
            CompilationUnit original = cu.clone();
            cu.accept(new HamcrestApiVisitor(), null);
            if (!cu.equals(original)) {
                Files.write(javaFile, cu.toString().getBytes());
                System.out.println("Transformed: " + javaFile);
                transformed++;
            }
        }
        
        System.out.println("Transformed " + transformed + " files");
    }
    
    private static class HamcrestApiVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            String typeName = expr.getType().asString();
            
            if (typeName.equals("StringContains") || typeName.equals("org.hamcrest.core.StringContains")) {
                if (expr.getArguments().size() == 2) {
                    Expression firstArg = expr.getArguments().get(0);
                    Expression secondArg = expr.getArguments().get(1);
                    
                    if (firstArg.isBooleanLiteralExpr()) {
                        // For hamcrest-core:1.3, use StringContains constructor with just the string
                        // This loses the ignoreCase boolean parameter but compiles
                        ObjectCreationExpr replacement = new ObjectCreationExpr();
                        replacement.setType("org.hamcrest.core.StringContains");
                        replacement.getArguments().add(secondArg);
                        return replacement;
                    }
                } else if (expr.getArguments().size() == 1) {
                    Expression textArg = expr.getArguments().get(0);
                    // Use containsString() static method which is available in both hamcrest-core:1.3 and hamcrest:2.2
                    MethodCallExpr replacement = new MethodCallExpr(
                        "org.hamcrest.core.StringContains.containsString",
                        textArg
                    );
                    return replacement;
                }
            } else if (typeName.equals("StringStartsWith") || typeName.equals("org.hamcrest.core.StringStartsWith")) {
                if (expr.getArguments().size() == 2) {
                    Expression firstArg = expr.getArguments().get(0);
                    Expression secondArg = expr.getArguments().get(1);
                    
                    if (firstArg.isBooleanLiteralExpr()) {
                        // For hamcrest-core:1.3, use StringStartsWith constructor with just the string
                        // This loses the ignoreCase boolean parameter but compiles
                        ObjectCreationExpr replacement = new ObjectCreationExpr();
                        replacement.setType("org.hamcrest.core.StringStartsWith");
                        replacement.getArguments().add(secondArg);
                        return replacement;
                    }
                } else if (expr.getArguments().size() == 1) {
                    Expression textArg = expr.getArguments().get(0);
                    // Use startsWith() static method which is available in both hamcrest-core:1.3 and hamcrest:2.2
                    MethodCallExpr replacement = new MethodCallExpr(
                        "org.hamcrest.core.StringStartsWith.startsWith",
                        textArg
                    );
                    return replacement;
                }
            }
            
            return super.visit(expr, arg);
        }
    }
}
