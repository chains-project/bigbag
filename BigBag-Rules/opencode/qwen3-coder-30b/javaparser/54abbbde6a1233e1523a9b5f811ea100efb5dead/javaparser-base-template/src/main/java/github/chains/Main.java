package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        File directory = new File(sourceDir);
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        List<File> javaFiles = findJavaFiles(directory);
        for (File file : javaFiles) {
            System.out.println("Processing: " + file.getAbsolutePath());
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // Apply transformations
            TvConstantReplacer replacer = new TvConstantReplacer();
            cu.accept(replacer, null);
            
            // Write back the modified file
            Files.write(file.toPath(), cu.toString().getBytes());
        }
    }

    private static List<File> findJavaFiles(File directory) throws IOException {
        List<File> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(directory.getAbsolutePath()))
            .filter(path -> path.toString().endsWith(".java"))
            .filter(Files::isRegularFile)
            .forEach(path -> javaFiles.add(path.toFile()));
        return javaFiles;
    }
}

// Visitor to replace Tv constant field accesses with literal values
class TvConstantReplacer extends ModifierVisitor<Void> {
    @Override
    public Visitable visit(FieldAccessExpr fae, Void arg) {
        // Check if this is a field access of com.jcabi.aspects.Tv
        if (fae.getScope() != null && 
            fae.getScope().toString().equals("com.jcabi.aspects.Tv")) {
            
            String fieldName = fae.getName().asString();
            
            // Replace Tv.SEVEN with 7
            if ("SEVEN".equals(fieldName)) {
                return new IntegerLiteralExpr("7");
            }
            // Replace Tv.TEN with 10
            else if ("TEN".equals(fieldName)) {
                return new IntegerLiteralExpr("10");
            }
            // Replace Tv.MILLION with 1000000
            else if ("MILLION".equals(fieldName)) {
                return new IntegerLiteralExpr("1000000");
            }
        }
        return super.visit(fae, arg);
    }
}