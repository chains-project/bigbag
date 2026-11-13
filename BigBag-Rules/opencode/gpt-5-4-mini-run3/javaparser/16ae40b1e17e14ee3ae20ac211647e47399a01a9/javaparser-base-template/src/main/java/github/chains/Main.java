package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        final Path sourceRoot = Paths.get(args[0]);
        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path)
                            && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;

            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                if (!importDeclaration.isAsterisk()
                        && importDeclaration.getNameAsString().equals(OLD_TYPE)) {
                    importDeclaration.setName(NEW_TYPE);
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }
}
