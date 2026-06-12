package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        String filePath = "/workspace/singer/thrift-logger/src/main/java/com/pinterest/singer/client/logback/AppenderUtils.java";
        File file = new File(filePath);
        CompilationUnit cu = StaticJavaParser.parse(file);

        // 1. Replace Imports
        cu.getImports().forEach(importDeclaration -> {
            if (importDeclaration.getNameAsString().equals("org.apache.thrift.transport.TFastFramedTransport")) {
                importDeclaration.setName("org.apache.thrift.transport.TFramedTransport");
            }
        });

        // 2. Replace class usage and object creation
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceType n, Void arg) {
                if (n.getNameAsString().equals("TFastFramedTransport")) {
                    n.setName("TFramedTransport");
                }
                super.visit(n, arg);
            }

            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                if (n.getType().getNameAsString().equals("TFastFramedTransport")) {
                    n.setType("TFramedTransport");
                }
                super.visit(n, arg);
            }
        }, null);

        // Save the file
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(cu.toString());
        }
        
        System.out.println("Transformation complete.");
    }
}