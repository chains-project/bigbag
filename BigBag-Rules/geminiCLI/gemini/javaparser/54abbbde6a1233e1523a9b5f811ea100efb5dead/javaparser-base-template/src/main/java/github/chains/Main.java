package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        processFile("/workspace/jcabi-ssh/src/main/java/com/jcabi/ssh/Ssh.java");
        processFile("/workspace/jcabi-ssh/src/main/java/com/jcabi/ssh/SshByPassword.java");
    }

    private static void processFile(String filePath) throws IOException {
        System.out.println("Processing: " + filePath);
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));

        // Remove import
        cu.findAll(ImportDeclaration.class).stream()
            .filter(i -> i.getNameAsString().equals("com.jcabi.aspects.Tv"))
            .forEach(ImportDeclaration::remove);

        // Replace Tv.SEVEN, Tv.TEN, Tv.MILLION
        cu.findAll(FieldAccessExpr.class).stream()
            .filter(fa -> fa.getScope().toString().equals("Tv"))
            .forEach(fa -> {
                String name = fa.getNameAsString();
                if (name.equals("SEVEN")) {
                    fa.replace(new IntegerLiteralExpr("7"));
                } else if (name.equals("TEN")) {
                    fa.replace(new LongLiteralExpr("10L"));
                } else if (name.equals("MILLION")) {
                    fa.replace(new IntegerLiteralExpr("1000000"));
                }
            });

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(cu.toString());
        }
        System.out.println("Finished: " + filePath);
    }
}