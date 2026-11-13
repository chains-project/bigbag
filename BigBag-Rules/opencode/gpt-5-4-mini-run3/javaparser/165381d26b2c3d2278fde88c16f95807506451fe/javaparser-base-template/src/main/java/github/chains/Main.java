package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_FQCN = "com.github.javaparser.printer.PrettyPrinterConfiguration";
    private static final String NEW_FQCN = "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        }
    }

    private static void rewrite(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            cu.accept(new PrettyPrinterConfigurationMigration(), null);
            Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static final class PrettyPrinterConfigurationMigration extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ClassOrInterfaceType type, Void arg) {
            ClassOrInterfaceType visited = (ClassOrInterfaceType) super.visit(type, arg);
            if (OLD_FQCN.equals(visited.toString())) {
                return StaticJavaParser.parseClassOrInterfaceType(NEW_FQCN);
            }
            return visited;
        }

        @Override
        public Visitable visit(CompilationUnit cu, Void arg) {
            cu.getImports().stream()
                .filter(i -> i.getNameAsString().equals(OLD_FQCN))
                .forEach(i -> i.setName(NEW_FQCN));
            return super.visit(cu, arg);
        }
    }
}
