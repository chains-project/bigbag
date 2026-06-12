package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root argument");
        }
        final Path root = Paths.get(args[0]);
        final CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        solver.add(new JavaParserTypeSolver(root));
        StaticJavaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(solver));
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        }
    }

    private static void rewrite(final Path path) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(unit);
            unit.accept(new AcceptTransformer(), null);
            Files.write(path, LexicalPreservingPrinter.print(unit).getBytes(StandardCharsets.UTF_8));
        } catch (final Exception err) {
            throw new IllegalStateException(String.format("Failed to transform %s", path), err);
        }
    }

    private static final class AcceptTransformer extends VoidVisitorAdapter<Void> {

        @Override
        public void visit(final MethodCallExpr node, final Void arg) {
            super.visit(node, arg);
            if (isOldConnectionAccept(node)) {
                final Expression headers = node.getArgument(1);
                node.setArgument(1, newHeadersFrom(headers));
            }
        }

        private static boolean isOldConnectionAccept(final MethodCallExpr node) {
            if (!"accept".equals(node.getNameAsString()) || node.getArguments().size() != 3) {
                return false;
            }
            try {
                final ResolvedMethodDeclaration resolved = node.resolve();
                return "com.artipie.http.Connection.accept(com.artipie.http.rs.RsStatus, java.lang.Iterable, org.reactivestreams.Publisher)"
                    .equals(resolved.getQualifiedSignature());
            } catch (final Exception err) {
                return false;
            }
        }

        private static ObjectCreationExpr newHeadersFrom(final Expression headers) {
            return new ObjectCreationExpr(
                null,
                StaticJavaParser.parseClassOrInterfaceType("com.artipie.http.Headers.From"),
                com.github.javaparser.ast.NodeList.nodeList(headers)
            );
        }
    }
}
