package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_METHOD = "accept";

    public static void main(final String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }
        final Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(String.format("Not a directory: %s", root));
        }
        final CombinedTypeSolver solver = new CombinedTypeSolver(
            new ReflectionTypeSolver(),
            new JavaParserTypeSolver(root)
        );
        final ParserConfiguration config = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11)
            .setSymbolResolver(new JavaSymbolSolver(solver));
        StaticJavaParser.setConfiguration(config);
        final List<Path> sources;
        try (Stream<Path> walk = Files.walk(root)) {
            sources = walk.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
        for (final Path source : sources) {
            rewrite(source);
        }
    }

    private static void rewrite(final Path source) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(unit);
        boolean changed = false;
        for (final MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            if (shouldWrapHeaders(call)) {
                final Expression headers = call.getArgument(1);
                call.setArgument(1, wrapHeaders(headers));
                changed = true;
            }
        }
        if (changed) {
            Files.writeString(source, LexicalPreservingPrinter.print(unit));
        }
    }

    private static boolean shouldWrapHeaders(final MethodCallExpr call) {
        if (!OLD_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 3) {
            return false;
        }
        final Expression headers = call.getArgument(1);
        final String text = headers.toString();
        if (text.startsWith("new ") && text.contains("Headers.From")) {
            return false;
        }
        if (text.contains("Headers.EMPTY") || text.contains("Headers.From")) {
            return false;
        }
        return true;
    }

    private static Expression wrapHeaders(final Expression headers) {
        return new ObjectCreationExpr(
            null,
            new ClassOrInterfaceType(null, "com.artipie.http.Headers.From"),
            NodeList.nodeList(headers.clone())
        );
    }
}
