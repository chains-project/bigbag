package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.interceptor";
    private static final String NEW_PREFIX = "jakarta.interceptor";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        try (Stream<Path> paths = Files.walk(sourceRoot, FileVisitOption.FOLLOW_LINKS)) {
            paths.filter(Main::isJavaSource)
                    .forEach(path -> rewriteFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan " + sourceRoot, e);
        }
    }

    private static boolean isJavaSource(Path path) {
        String normalized = path.toString();
        return Files.isRegularFile(path)
                && normalized.endsWith(".java")
                && !normalized.contains("/target/")
                && !normalized.contains("\\target\\");
    }

    private static void rewriteFile(JavaParser parser, Path path) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(path);
            if (result.getResult().isEmpty()) {
                return;
            }

            CompilationUnit compilationUnit = result.getResult().get();
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = rewriteInterceptorReferences(compilationUnit);
            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean rewriteInterceptorReferences(CompilationUnit compilationUnit) {
        boolean changed = false;

        List<Name> names = new ArrayList<>(compilationUnit.findAll(Name.class));
        for (Name name : names) {
            changed |= replaceQualifiedName(name, OLD_PREFIX, NEW_PREFIX);
        }

        List<ClassOrInterfaceType> types = new ArrayList<>(compilationUnit.findAll(ClassOrInterfaceType.class));
        for (ClassOrInterfaceType type : types) {
            changed |= replaceQualifiedType(type, OLD_PREFIX, NEW_PREFIX);
        }

        return changed;
    }

    private static boolean replaceQualifiedName(Name name, String oldPrefix, String newPrefix) {
        String value = name.asString();
        if (value.equals(oldPrefix) || value.startsWith(oldPrefix + ".")) {
            name.replace(StaticJavaParser.parseName(value.replace(oldPrefix, newPrefix)));
            return true;
        }
        return false;
    }

    private static boolean replaceQualifiedType(ClassOrInterfaceType type, String oldPrefix, String newPrefix) {
        String value = type.asString();
        if (value.equals(oldPrefix) || value.startsWith(oldPrefix + ".")) {
            type.replace(StaticJavaParser.parseClassOrInterfaceType(value.replace(oldPrefix, newPrefix)));
            return true;
        }
        return false;
    }
}
