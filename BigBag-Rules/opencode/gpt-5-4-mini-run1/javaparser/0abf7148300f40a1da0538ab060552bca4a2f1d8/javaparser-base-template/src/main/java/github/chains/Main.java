package github.chains;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

public class Main {
    public static void main(String[] args) {
        final Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        final ParserConfiguration configuration = new ParserConfiguration();
        final JavaParser parser = new JavaParser(configuration);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> rewriteFile(parser, path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void rewriteFile(JavaParser parser, Path path) {
        try {
            final CompilationUnit cu = parser.parse(path).getResult()
                    .orElseThrow(() -> new IllegalStateException("Failed to parse " + path));
            LexicalPreservingPrinter.setup(cu);

            final boolean[] changed = { false };
            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (shouldBoxLineWidthArgument(call)) {
                    boxLineWidthArgument(call);
                    changed[0] = true;
                }
            });

            if (changed[0]) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean shouldBoxLineWidthArgument(MethodCallExpr call) {
        if (!"setLineWidth".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
            return false;
        }

        final Expression argument = call.getArgument(0);
        if (!(argument instanceof MethodCallExpr)) {
            return true;
        }

        final MethodCallExpr methodCall = (MethodCallExpr) argument;
        return !(methodCall.getScope().isPresent()
                && methodCall.getScope().get().isNameExpr()
                && "Float".equals(methodCall.getScope().get().asNameExpr().getNameAsString())
                && "valueOf".equals(methodCall.getNameAsString()));
    }

    private static void boxLineWidthArgument(MethodCallExpr call) {
        final Expression original = call.getArgument(0).clone();
        final CastExpr cast = new CastExpr(PrimitiveType.floatType(), original);
        final MethodCallExpr boxed = new MethodCallExpr(new NameExpr("Float"), "valueOf", NodeList.nodeList(cast));
        call.setArgument(0, boxed);
    }
}
