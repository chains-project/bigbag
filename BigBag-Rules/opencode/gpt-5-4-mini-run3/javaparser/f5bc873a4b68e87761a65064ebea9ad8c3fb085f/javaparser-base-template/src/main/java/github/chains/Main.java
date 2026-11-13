package github.chains;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Main {

    private static final String MAVEN_ARCHIVER_FQN = "org.apache.maven.archiver.MavenArchiver";
    private static final List<MethodRename> RENAMES = Arrays.asList(
            new MethodRename(MAVEN_ARCHIVER_FQN, "configureReproducible", "configureReproducibleBuild", 1),
            new MethodRename(MAVEN_ARCHIVER_FQN, "parseOutputTimestamp", "parseBuildOutputTimestamp", 1)
    );

    public static void main(String[] args) throws IOException {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        AtomicInteger changedFiles = new AtomicInteger();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            if (transform(path)) {
                                changedFiles.incrementAndGet();
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to transform " + path, e);
                        }
                    });
        } catch (ParseProblemException e) {
            throw new IllegalStateException("Failed to parse source files", e);
        }

        System.out.println("Updated files: " + changedFiles.get());
    }

    private static boolean transform(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            for (MethodRename rename : RENAMES) {
                if (rename.matches(call) && !call.getNameAsString().equals(rename.newName)) {
                    call.setName(rename.newName);
                    changed = true;
                }
            }
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
        return changed;
    }

    private static final class MethodRename {
        private final String ownerFqn;
        private final String oldName;
        private final String newName;
        private final int argCount;

        private MethodRename(String ownerFqn, String oldName, String newName, int argCount) {
            this.ownerFqn = ownerFqn;
            this.oldName = oldName;
            this.newName = newName;
            this.argCount = argCount;
        }

        private boolean matches(MethodCallExpr call) {
            return call.getNameAsString().equals(oldName) && call.getArguments().size() == argCount;
        }
    }
}
