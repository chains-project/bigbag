package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        File file = new File("/workspace/jcabi-simpledb/src/test/java/com/jcabi/simpledb/RegionITCase.java");
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        // Setup LexicalPreservingPrinter
        LexicalPreservingPrinter.setup(cu);

        // Remove import com.jcabi.aspects.Tv;
        cu.findAll(ImportDeclaration.class).stream()
            .filter(i -> i.getNameAsString().equals("com.jcabi.aspects.Tv"))
            .forEach(ImportDeclaration::remove);

        // Replace Tv.TEN with 10
        cu.findAll(FieldAccessExpr.class).stream()
            .filter(f -> f.toString().equals("Tv.TEN"))
            .forEach(f -> f.replace(new IntegerLiteralExpr("10")));
            
        // Replace Tv.EIGHT with 8
        cu.findAll(FieldAccessExpr.class).stream()
            .filter(f -> f.toString().equals("Tv.EIGHT"))
            .forEach(f -> f.replace(new IntegerLiteralExpr("8")));

        // Save the file using LexicalPreservingPrinter
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(LexicalPreservingPrinter.print(cu));
        }
    }
}
