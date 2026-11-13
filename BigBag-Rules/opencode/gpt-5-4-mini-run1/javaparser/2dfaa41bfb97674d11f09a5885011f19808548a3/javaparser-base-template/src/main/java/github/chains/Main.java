package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_OWNER = "org.mockito.invocation.InvocationOnMock";
    private static final String OLD_METHOD = "getArgumentAt";
    private static final String NEW_METHOD = "getArgument";

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        for (Path sourceRoot : findJavaSourceRoots(root)) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        }

        ParserConfiguration configuration = new ParserConfiguration()
            .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser parser = new JavaParser(configuration);

        Files.walk(root)
            .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
            .forEach(path -> transformFile(parser, path));
    }

    private static List<Path> findJavaSourceRoots(Path root) throws IOException {
        List<Path> roots = new ArrayList<>();
        Files.walk(root)
            .filter(path -> Files.isDirectory(path))
            .filter(path -> path.endsWith(Paths.get("src", "main", "java"))
                || path.endsWith(Paths.get("src", "test", "java"))
                || path.endsWith(Paths.get("src", "it", "java"))
                || path.endsWith(Paths.get("src", "testFixtures", "java")))
            .forEach(roots::add);
        if (roots.isEmpty()) {
            roots.add(root);
        }
        return roots;
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            String original = Files.readString(path, StandardCharsets.UTF_8);
            CompilationUnit cu = parser.parse(original).getResult().orElse(null);
            if (cu == null) {
                return;
            }
            boolean[] changed = new boolean[1];
            LexicalPreservingPrinter.setup(cu);
            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (shouldRewrite(call)) {
                    call.setName(NEW_METHOD);
                    changed[0] = true;
                }
            });
            if (changed[0]) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException ignored) {
            // Skip files that cannot be parsed or resolved cleanly.
        }
    }

    private static boolean shouldRewrite(MethodCallExpr call) {
        if (!OLD_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 2) {
            return false;
        }
        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            return OLD_OWNER.equals(resolved.declaringType().getQualifiedName());
        } catch (UnsolvedSymbolException | UnsupportedOperationException | IllegalStateException ignored) {
            return true;
        }
    }
}
