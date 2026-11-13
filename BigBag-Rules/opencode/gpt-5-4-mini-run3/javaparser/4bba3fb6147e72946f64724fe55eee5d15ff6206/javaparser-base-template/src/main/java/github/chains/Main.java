package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            boolean changed = false;

            for (ImportDeclaration importDeclaration : new ArrayList<>(compilationUnit.getImports())) {
                String original = importDeclaration.getNameAsString();
                String rewritten = rewriteAnnotationPackage(original);
                if (!original.equals(rewritten)) {
                    importDeclaration.setName(StaticJavaParser.parseName(rewritten));
                    changed = true;
                }
            }

            List<Name> names = new ArrayList<>(compilationUnit.findAll(Name.class));
            for (Name name : names) {
                String original = name.asString();
                String rewritten = rewriteAnnotationPackage(original);
                if (!original.equals(rewritten)) {
                    name.replace(StaticJavaParser.parseName(rewritten));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, compilationUnit.toString());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite " + file, e);
        }
    }

    private static String rewriteAnnotationPackage(String value) {
        if (value.startsWith("javax.annotation")) {
            return "jakarta.annotation" + value.substring("javax.annotation".length());
        }
        return value;
    }
}
