package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "com.mysql.jdbc.exceptions.MySQLTimeoutException";
    private static final String NEW_TYPE = "com.mysql.cj.jdbc.exceptions.MySQLTimeoutException";
    private static final String OLD_DRIVER = "com.mysql.jdbc.Driver";
    private static final String NEW_DRIVER = "com.mysql.cj.jdbc.Driver";

    public static void main(String[] args) throws IOException {
        final Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            boolean changed = false;

            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                if (OLD_TYPE.equals(importDeclaration.getNameAsString())) {
                    importDeclaration.setName(NEW_TYPE);
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
                if (isOldTypeReference(type)) {
                    type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                    changed = true;
                }
            }

            for (StringLiteralExpr literal : compilationUnit.findAll(StringLiteralExpr.class)) {
                if (OLD_TYPE.equals(literal.asString())) {
                    literal.setString(NEW_TYPE);
                    changed = true;
                } else if (OLD_DRIVER.equals(literal.asString())) {
                    literal.setString(NEW_DRIVER);
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, compilationUnit.toString());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to rewrite " + file, ex);
        }
    }

    private static boolean isOldTypeReference(ClassOrInterfaceType type) {
        if (OLD_TYPE.equals(type.toString())) {
            return true;
        }

        if (!type.getNameAsString().equals("MySQLTimeoutException")) {
            return false;
        }

        return true;
    }
}
