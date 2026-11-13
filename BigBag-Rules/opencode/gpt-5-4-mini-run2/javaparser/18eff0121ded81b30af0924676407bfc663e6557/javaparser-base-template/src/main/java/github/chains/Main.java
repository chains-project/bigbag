package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String OLD_PACKAGE = "com.gargoylesoftware.htmlunit";
    private static final String NEW_PACKAGE = "org.htmlunit";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single argument: source directory");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try {
            transformSourceTree(sourceRoot);
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform sources", exception);
        }
    }

    private static void transformSourceTree(Path sourceRoot) throws IOException {
        List<Path> javaFiles = Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .collect(Collectors.toList());

        for (Path javaFile : javaFiles) {
            String original = Files.readString(javaFile, StandardCharsets.UTF_8);
            CompilationUnit compilationUnit = StaticJavaParser.parse(original);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean modified = false;
            modified |= updateImports(compilationUnit);
            modified |= updateTypes(compilationUnit);

            if (modified) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        }
    }

    private static boolean updateImports(CompilationUnit compilationUnit) {
        boolean modified = false;

        for (int i = 0; i < compilationUnit.getImports().size(); i++) {
            String importName = compilationUnit.getImport(i).getNameAsString();
            if (importName.startsWith(OLD_PACKAGE)) {
                String replacement = importName.replace(OLD_PACKAGE, NEW_PACKAGE);
                compilationUnit.getImport(i).setName(replacement);
                modified = true;
            }
        }

        return modified;
    }

    private static boolean updateTypes(CompilationUnit compilationUnit) {
        boolean modified = false;

        for (Type type : compilationUnit.findAll(Type.class)) {
            String typeAsString = type.asString();
            if (typeAsString.contains(OLD_PACKAGE)) {
                String replacement = typeAsString.replace(OLD_PACKAGE, NEW_PACKAGE);
                type.replace(StaticJavaParser.parseType(replacement));
                modified = true;
            }
        }

        return modified;
    }
}
