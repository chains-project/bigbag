package github.chains;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "com.google.api.services.cloudresourcemanager.";
    private static final String NEW_PREFIX = "com.google.api.services.cloudresourcemanager.v3.";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        StaticJavaParser.setConfiguration(configuration);

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            compilationUnit.accept(new PackageRenameVisitor(), null);
            String updated = LexicalPreservingPrinter.print(compilationUnit);
            if (!Objects.equals(updated, Files.readString(file, StandardCharsets.UTF_8))) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException | ParseProblemException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static final class PackageRenameVisitor extends ModifierVisitor<Void> {
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            String name = n.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                n.setName(name.replace(OLD_PREFIX, NEW_PREFIX));
            }
            return n;
        }

        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            String rendered = n.toString();
            if (rendered.contains(OLD_PREFIX)) {
                return StaticJavaParser.parseClassOrInterfaceType(rendered.replace(OLD_PREFIX, NEW_PREFIX));
            }
            return n;
        }
    }
}
