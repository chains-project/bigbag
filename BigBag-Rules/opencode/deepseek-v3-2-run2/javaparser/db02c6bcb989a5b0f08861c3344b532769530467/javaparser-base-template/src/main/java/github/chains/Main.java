package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory> <class1,class2,...>");
            System.err.println("Example: java -cp target/classes github.chains.Main /path/to/src StringContains,StringStartsWith");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String[] classNames = args[1].split(",");
        
        System.out.println("Transforming constructors for classes: " + Arrays.toString(classNames) + " in " + sourceDir);
        
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> transformFile(path, classNames));
    }
    
    private static void transformFile(Path path, String[] classNames) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(path).getResult().orElseThrow();
            
            ConstructorTransformer transformer = new ConstructorTransformer(classNames);
            transformer.visit(cu, null);
            
            if (transformer.modified) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Modified: " + path);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
        }
    }
    
    private static class ConstructorTransformer extends ModifierVisitor<Void> {
        private final Set<String> targetClasses;
        private boolean modified = false;
        
        ConstructorTransformer(String[] classNames) {
            this.targetClasses = new HashSet<>(Arrays.asList(classNames));
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            
            String typeName = expr.getType().getNameAsString();
            
            if (targetClasses.contains(typeName)) {
                NodeList<Expression> args = expr.getArguments();
                
                if (args.size() == 2 && args.get(0) instanceof BooleanLiteralExpr) {
                    expr.getArguments().remove(0);
                    modified = true;
                }
            }
            
            return expr;
        }
    }
}