package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java");
        StaticJavaParser.getConfiguration().setAttributeComments(true);
        CompilationUnit cu = StaticJavaParser.parse(path);
        LexicalPreservingPrinter.setup(cu);

        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("net.lingala.zip4j.core.ZipFile")) {
                importDecl.setName("net.lingala.zip4j.ZipFile");
            }
        }

        Files.write(path, LexicalPreservingPrinter.print(cu).getBytes());
        System.out.println("Transformed successfully.");
    }
}