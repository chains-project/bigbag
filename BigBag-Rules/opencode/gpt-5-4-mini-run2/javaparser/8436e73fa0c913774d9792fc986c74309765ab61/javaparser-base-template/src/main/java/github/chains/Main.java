package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_JWS_PREFIX = "javax.jws";
    private static final String NEW_JWS_PREFIX = "jakarta.jws";

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        ParserConfiguration configuration = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        StaticJavaParser.setConfiguration(configuration);

        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transformFile(file);
            }
        }
    }

    private static void transformFile(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (!source.contains(OLD_JWS_PREFIX)) {
            return;
        }

        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;
        for (Name name : compilationUnit.findAll(Name.class)) {
            String text = name.asString();
            if (text.equals(OLD_JWS_PREFIX) || text.startsWith(OLD_JWS_PREFIX + ".")) {
                name.replace(StaticJavaParser.parseName(text.replace(OLD_JWS_PREFIX, NEW_JWS_PREFIX)));
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }
}
