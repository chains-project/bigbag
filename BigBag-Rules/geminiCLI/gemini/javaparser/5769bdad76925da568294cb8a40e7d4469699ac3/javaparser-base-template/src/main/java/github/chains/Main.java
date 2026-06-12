package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        String filePath = "/workspace/incrementals-tools/lib/src/main/java/io/jenkins/tools/incrementals/lib/UpdateChecker.java";
        File file = new File(filePath);
        
        System.out.println("Parsing " + filePath);
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        cu.findAll(FieldAccessExpr.class).forEach(fa -> {
            if (fa.getNameAsString().equals("status")) {
                System.out.println("Found field access 'status' at line " + fa.getBegin().map(p -> p.line).orElse(-1));
                // Replace .status with .getStatus()
                MethodCallExpr getter = new MethodCallExpr(fa.getScope(), "getStatus");
                fa.replace(getter);
                System.out.println("Replaced with .getStatus()");
            }
        });
        
        Files.write(Paths.get(filePath), cu.toString().getBytes());
        System.out.println("Saved modified file.");
    }
}