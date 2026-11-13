package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_FQCN = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_FQCN = "org.codehaus.plexus.configuration.PlexusConfiguration";
    private static final String OLD_SIMPLE = "Xpp3Dom";
    private static final String NEW_SIMPLE = "PlexusConfiguration";

    static {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }
        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transform);
        }
    }

    private static void transform(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            AtomicBoolean changed = new AtomicBoolean(false);

            compilationUnit.getImports().forEach(importDeclaration -> {
                String name = importDeclaration.getNameAsString();
                if (name.equals(OLD_FQCN)) {
                    importDeclaration.setName(NEW_FQCN);
                    changed.set(true);
                }
            });

            for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
                if (type.asString().equals(OLD_FQCN) || type.getNameAsString().equals(OLD_SIMPLE)) {
                    type.setName(NEW_SIMPLE);
                    changed.set(true);
                }
            }

            if (changed.get()) {
                Files.writeString(file, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (RuntimeException exception) {
            // Skip files that use language features outside the parser level.
        } catch (Exception exception) {
            throw new RuntimeException("Failed to transform " + file, exception);
        }
    }
}
