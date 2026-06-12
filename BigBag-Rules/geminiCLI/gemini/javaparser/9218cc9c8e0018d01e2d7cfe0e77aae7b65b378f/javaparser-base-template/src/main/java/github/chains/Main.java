package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        String[] dirs = {
            "/workspace/wicket-crudifier/src/main/java",
            "/workspace/wicket-crudifier/src/test/java"
        };
        for (String dir : dirs) {
            try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".java"))
                     .forEach(p -> {
                         try {
                             CompilationUnit cu = StaticJavaParser.parse(p);
                             final boolean[] changed = {false};
                             
                             cu.findAll(ImportDeclaration.class).forEach(id -> {
                                 String name = id.getNameAsString();
                                 if (name.startsWith("javax.validation")) {
                                     id.setName(name.replace("javax.validation", "jakarta.validation"));
                                     changed[0] = true;
                                 }
                             });
                             
                             cu.findAll(FieldAccessExpr.class).forEach(n -> {
                                 if (n.getNameAsString().equals("validatorFactory") &&
                                     n.getScope().toString().equals("HibernateValidatorProperty")) {
                                     n.replace(StaticJavaParser.parseExpression("jakarta.validation.Validation.buildDefaultValidatorFactory()"));
                                     changed[0] = true;
                                 }
                             });
                             
                             if (changed[0]) {
                                 Files.write(p, cu.toString().getBytes());
                                 System.out.println("Modified " + p);
                             }
                         } catch (Exception e) {
                             e.printStackTrace();
                         }
                     });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}