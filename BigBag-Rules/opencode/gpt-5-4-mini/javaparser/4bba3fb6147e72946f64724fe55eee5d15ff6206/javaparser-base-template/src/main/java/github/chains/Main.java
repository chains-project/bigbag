package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String OLD_PREFIX = "javax.annotation";
    private static final String NEW_PREFIX = "jakarta.annotation";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewrite);
        }
    }

    private static void rewrite(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = transform(compilationUnit);
            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean transform(CompilationUnit compilationUnit) {
        boolean changed = false;

        List<Name> names = compilationUnit.findAll(Name.class);
        for (Name name : names) {
            String value = name.asString();
            if (value.equals(OLD_PREFIX) || value.startsWith(OLD_PREFIX + ".")) {
                name.replace(StaticJavaParser.parseName(value.replaceFirst(OLD_PREFIX, NEW_PREFIX)));
                changed = true;
            }
        }

        return changed;
    }
}
