package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Path filePath = Paths.get("/workspace/pdb/src/main/java/com/feedzai/commons/sql/abstraction/engine/impl/mysql/MySqlQueryExceptionHandler.java");
        CompilationUnit cu = StaticJavaParser.parse(filePath);
        
        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().equals("com.mysql.jdbc.exceptions.MySQLTimeoutException")) {
                id.setName("com.mysql.cj.jdbc.exceptions.MySQLTimeoutException");
            }
        }
        
        Files.write(filePath, cu.toString().getBytes());
        System.out.println("Transformation applied successfully.");
    }
}
