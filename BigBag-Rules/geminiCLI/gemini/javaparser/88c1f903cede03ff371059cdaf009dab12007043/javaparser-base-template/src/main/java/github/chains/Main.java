package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class Main {
    public static void main(String[] args) throws FileNotFoundException {
        File file = new File("/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java");
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("net.lingala.zip4j.core.ZipFile")) {
                importDecl.setName("net.lingala.zip4j.ZipFile");
            }
        }

        try (PrintWriter out = new PrintWriter(file)) {
            out.println(LexicalPreservingPrinter.print(cu));
        }
    }
}
