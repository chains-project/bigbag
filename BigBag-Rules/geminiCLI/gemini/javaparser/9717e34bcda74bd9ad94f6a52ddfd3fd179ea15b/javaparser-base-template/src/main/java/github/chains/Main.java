package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        String projectPath = "/workspace/jcabi-github";
        
        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }

    private static void processFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean modified = false;

            // Remove import
            if (cu.findAll(ImportDeclaration.class).stream()
                    .anyMatch(i -> i.getNameAsString().equals("com.jcabi.aspects.Tv"))) {
                cu.findAll(ImportDeclaration.class).stream()
                        .filter(i -> i.getNameAsString().equals("com.jcabi.aspects.Tv"))
                        .forEach(ImportDeclaration::remove);
                modified = true;
            }

            // Replace Tv.TWENTY with 20, Tv.TEN with 10, etc.
            
            for (FieldAccessExpr fa : cu.findAll(FieldAccessExpr.class)) {
                if (fa.getScope().isNameExpr() && fa.getScope().asNameExpr().getNameAsString().equals("Tv")) {
                    String name = fa.getNameAsString();
                    String value = null;
                    switch (name) {
                        case "ZERO": value = "0"; break;
                        case "ONE": value = "1"; break;
                        case "TWO": value = "2"; break;
                        case "THREE": value = "3"; break;
                        case "FOUR": value = "4"; break;
                        case "FIVE": value = "5"; break;
                        case "SIX": value = "6"; break;
                        case "SEVEN": value = "7"; break;
                        case "EIGHT": value = "8"; break;
                        case "NINE": value = "9"; break;
                        case "TEN": value = "10"; break;
                        case "FIFTEEN": value = "15"; break;
                        case "TWENTY": value = "20"; break;
                        case "THIRTY": value = "30"; break;
                        case "FORTY": value = "40"; break;
                        case "FIFTY": value = "50"; break;
                        case "SIXTY": value = "60"; break;
                        case "SEVENTY": value = "70"; break;
                        case "EIGHTY": value = "80"; break;
                        case "NINETY": value = "90"; break;
                        case "HUNDRED": value = "100"; break;
                        case "THOUSAND": value = "1000"; break;
                        case "MILLION": value = "1000000"; break;
                        case "BILLION": value = "1000000000"; break;
                    }
                    
                    if (value != null) {
                        fa.replace(new IntegerLiteralExpr(value));
                        modified = true;
                    }
                }
            }

            if (modified) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Fixed " + path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}