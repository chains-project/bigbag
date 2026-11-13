package test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class TestParser {
    public static void main(String[] args) {
        String code = "new MapEntry<>(\"header\", \"value\")";
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                System.out.println("Found ObjectCreationExpr: " + n);
                System.out.println("Type: " + n.getType());
                System.out.println("Type as string: " + n.getType().toString());
                System.out.println("Type class: " + n.getType().getClass());
                super.visit(n, arg);
            }
        }, null);
    }
}