package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {
        File dir = new File("/workspace/java-pubsub-group-kafka-connector/src/main/java/com/google/pubsublite/kafka/sink");
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                if (file.getName().endsWith(".java")) {
                    processFile(file);
                }
            }
        }
        File testDir = new File("/workspace/java-pubsub-group-kafka-connector/src/test/java/com/google/pubsublite/kafka/sink");
        if (testDir.exists()) {
            for (File file : testDir.listFiles()) {
                if (file.getName().endsWith(".java")) {
                    processFile(file);
                }
            }
        }
    }

    private static void processFile(File file) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);

        ModifierVisitor<Void> visitor = new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals("setContext")) {
                    if (n.getScope().isPresent()) {
                        return n.getScope().get().accept(this, arg);
                    }
                }
                return super.visit(n, arg);
            }
        };

        visitor.visit(cu, null);
        Files.write(file.toPath(), cu.toString().getBytes());
    }
}
