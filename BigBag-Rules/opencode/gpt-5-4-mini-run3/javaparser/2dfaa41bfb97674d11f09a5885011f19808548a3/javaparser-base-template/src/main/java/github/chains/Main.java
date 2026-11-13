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
import java.util.Objects;

public class Main {
    private static final String OLD_TYPE = "org.mockito.invocation.InvocationOnMock";
    private static final String OLD_METHOD = "getArgumentAt";
    private static final String NEW_METHOD = "getArgument";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: Main <source-directory>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("not a directory: " + sourceRoot);
        }

        StaticJavaParser.setConfiguration(new ParserConfiguration());

        int changedFiles = 0;
        try (var paths = Files.walk(sourceRoot)) {
            for (Path file : (Iterable<Path>) paths.filter(path -> path.toString().endsWith(".java"))::iterator) {
                if (rewrite(file)) {
                    changedFiles++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("updated " + changedFiles + " file(s) for " + OLD_TYPE + "." + OLD_METHOD + " -> " + NEW_METHOD);
    }

    private static boolean rewrite(Path file) throws IOException {
        final CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(file);
        } catch (ParseProblemException e) {
            throw new RuntimeException("failed to parse " + file, e);
        }

        LexicalPreservingPrinter.setup(cu);
        boolean[] changed = {false};

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (matchesOldApi(call)) {
                call.setName(NEW_METHOD);
                changed[0] = true;
            }
        });

        if (!changed[0]) {
            return false;
        }

        Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        return true;
    }

    private static boolean matchesOldApi(MethodCallExpr call) {
        return Objects.equals(call.getNameAsString(), OLD_METHOD) && call.getArguments().size() == 2;
    }
}
