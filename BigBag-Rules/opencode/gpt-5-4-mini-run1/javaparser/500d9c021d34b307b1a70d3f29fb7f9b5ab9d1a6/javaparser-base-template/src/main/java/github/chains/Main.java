package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_PREFIX = "javax.mvc";
    private static final String NEW_PREFIX = "jakarta.mvc";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory path");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        JavaParser parser = new JavaParser();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> rewriteJavaFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteJavaFile(JavaParser parser, Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            if (!source.contains(OLD_PREFIX)) {
                return;
            }

            ParseResult<CompilationUnit> parseResult = parser.parse(source);
            Optional<CompilationUnit> compilationUnit = parseResult.getResult();
            if (compilationUnit.isEmpty()) {
                throw new IllegalStateException("Failed to parse Java file: " + path);
            }

            CompilationUnit cu = compilationUnit.get();
            LexicalPreservingPrinter.setup(cu);
            cu.accept(new NamespaceMigrationVisitor(), null);

            String rewritten = LexicalPreservingPrinter.print(cu);
            if (!rewritten.equals(source)) {
                Files.writeString(path, rewritten, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite Java file: " + path, e);
        }
    }

    private static final class NamespaceMigrationVisitor extends VoidVisitorAdapter<Void> {

        @Override
        public void visit(CompilationUnit cu, Void arg) {
            cu.getImports().forEach(importDeclaration -> {
                if (importDeclaration.getNameAsString().startsWith(OLD_PREFIX)) {
                    importDeclaration.setName(importDeclaration.getNameAsString().replaceFirst(OLD_PREFIX, NEW_PREFIX));
                }
            });
            cu.findAll(Name.class).forEach(name -> {
                String value = name.asString();
                if (value.startsWith(OLD_PREFIX)) {
                    name.replace(StaticJavaParser.parseName(value.replaceFirst(OLD_PREFIX, NEW_PREFIX)));
                }
            });
            cu.findAll(StringLiteralExpr.class).forEach(stringLiteralExpr -> {
                String value = stringLiteralExpr.asString();
                if (value.contains(OLD_PREFIX)) {
                    stringLiteralExpr.setString(value.replace(OLD_PREFIX, NEW_PREFIX));
                }
            });
            super.visit(cu, arg);
        }
    }
}
