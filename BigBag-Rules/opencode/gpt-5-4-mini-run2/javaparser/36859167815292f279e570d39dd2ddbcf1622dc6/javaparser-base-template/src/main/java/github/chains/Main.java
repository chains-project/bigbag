package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_FACTORY = "org.apache.fop.apps.FopFactory";
    private static final String NEW_FACTORY_BUILDER = "org.apache.fop.apps.FopFactoryBuilder";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        try {
            transform(Paths.get(args[0]));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void transform(Path root) throws IOException {
        ParserConfiguration configuration = new ParserConfiguration();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(root.toFile()));
        configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(configuration);

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        transformFile(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
    }

    private static void transformFile(Path path) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(path);
        LexicalPreservingPrinter.setup(compilationUnit);

        AtomicBoolean changed = new AtomicBoolean(false);
        compilationUnit.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                Visitable visited = super.visit(n, arg);
                if (!(visited instanceof MethodCallExpr)) {
                    return visited;
                }

                MethodCallExpr methodCallExpr = (MethodCallExpr) visited;
                if (isOldFopFactoryNewInstance(methodCallExpr)) {
                    changed.set(true);
                    return buildReplacement();
                }

                return methodCallExpr;
            }
        }, null);

        if (changed.get()) {
            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }

    private static boolean isOldFopFactoryNewInstance(MethodCallExpr methodCallExpr) {
        if (!"newInstance".equals(methodCallExpr.getNameAsString()) || !methodCallExpr.getArguments().isEmpty()) {
            return false;
        }

        if (methodCallExpr.getScope().isEmpty()) {
            return false;
        }

        try {
            if (methodCallExpr.resolve().declaringType().getQualifiedName().equals(OLD_FACTORY)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Fall back to a structural match when symbol resolution is incomplete.
        }

        return methodCallExpr.getScope()
            .map(Main::isFopFactoryScope)
            .orElse(false);
    }

    private static boolean isFopFactoryScope(Expression scope) {
        if (scope.isNameExpr()) {
            return "FopFactory".equals(scope.asNameExpr().getNameAsString());
        }

        if (scope.isFieldAccessExpr()) {
            return "FopFactory".equals(scope.asFieldAccessExpr().getNameAsString());
        }

        return scope.toString().endsWith(".FopFactory");
    }

    private static MethodCallExpr buildReplacement() {
        ObjectCreationExpr fileCreation = new ObjectCreationExpr(
            null,
            new ClassOrInterfaceType(null, "java.io.File"),
            NodeList.nodeList(new StringLiteralExpr("."))
        );

        MethodCallExpr baseUri = new MethodCallExpr(fileCreation, "toURI");
        ObjectCreationExpr builderCreation = new ObjectCreationExpr(
            null,
            new ClassOrInterfaceType(null, NEW_FACTORY_BUILDER),
            NodeList.nodeList(baseUri)
        );

        return new MethodCallExpr(builderCreation, "build");
    }
}
