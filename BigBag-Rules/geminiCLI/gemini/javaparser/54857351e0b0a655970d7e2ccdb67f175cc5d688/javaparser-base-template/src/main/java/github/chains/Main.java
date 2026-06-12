package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        String filePath = "/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java";
        
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        LexicalPreservingPrinter.setup(cu);
        
        for (ImportDeclaration importDecl : cu.findAll(ImportDeclaration.class)) {
            if (importDecl.getNameAsString().equals("net.lingala.zip4j.core.ZipFile")) {
                importDecl.setName("net.lingala.zip4j.ZipFile");
            }
        }
        
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(LexicalPreservingPrinter.print(cu));
        }
        
        System.out.println("Transformation complete.");
    }
}
