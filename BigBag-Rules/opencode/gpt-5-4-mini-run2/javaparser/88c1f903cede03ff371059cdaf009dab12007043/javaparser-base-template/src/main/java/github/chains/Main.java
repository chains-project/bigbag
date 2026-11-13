package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_FQCN = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_FQCN = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: source directory");
        }

        final Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        final JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> rewriteFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree", e);
        }
    }

    private static void rewriteFile(JavaParser parser, Path file) {
        try {
            final ParseResult<CompilationUnit> result = parser.parse(file);
            final Optional<CompilationUnit> compilationUnit = result.getResult();
            if (compilationUnit.isEmpty()) {
                return;
            }

            final CompilationUnit cu = compilationUnit.get();
            LexicalPreservingPrinter.setup(cu);
            boolean modified = false;

            for (ImportDeclaration importDeclaration : cu.getImports()) {
                if (!importDeclaration.isAsterisk()
                        && importDeclaration.getNameAsString().equals(OLD_FQCN)) {
                    importDeclaration.setName(NEW_FQCN);
                    modified = true;
                }
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if (type.toString().equals(OLD_FQCN)) {
                    type.setName("ZipFile");
                    type.removeScope();
                    modified = true;
                }
            }

            for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
                if (creationExpr.getType().toString().equals(OLD_FQCN)) {
                    creationExpr.setType(NEW_FQCN);
                    modified = true;
                }
            }

            if (modified) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }
}
