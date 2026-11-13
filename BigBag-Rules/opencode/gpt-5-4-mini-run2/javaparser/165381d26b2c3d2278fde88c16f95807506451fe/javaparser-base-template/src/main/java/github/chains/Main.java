package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String OLD_TYPE = "com.github.javaparser.printer.PrettyPrinterConfiguration";
    private static final String NEW_TYPE = "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration";

    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try {
            transform(root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transform(Path root) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        for (Path file : javaFiles) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (!source.contains(OLD_TYPE) && !source.contains("PrettyPrinterConfiguration")) {
                continue;
            }

            CompilationUnit cu = StaticJavaParser.parse(source);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            for (ImportDeclaration importDeclaration : cu.getImports()) {
                if (importDeclaration.getNameAsString().equals(OLD_TYPE)) {
                    importDeclaration.setName(NEW_TYPE);
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if (matchesOldPrettyPrinterConfiguration(type)) {
                    type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        }
    }

    private static boolean matchesOldPrettyPrinterConfiguration(ClassOrInterfaceType type) {
        if (!"PrettyPrinterConfiguration".equals(type.getNameAsString())) {
            return false;
        }

        Optional<ClassOrInterfaceType> scope = type.getScope();
        if (!scope.isPresent()) {
            return false;
        }

        return OLD_TYPE.equals(scope.get().asString() + "." + type.getNameAsString());
    }
}
