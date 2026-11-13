package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public final class Main {

    private static final String OLD_FACTORY = "com.artipie.asto.factory.Storages";

    private static final String NEW_FACTORY = "com.artipie.asto.factory.StoragesLoader";

    private static final String NEW_CONFIG = "com.artipie.asto.factory.Config.YamlStorageConfig";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }
        StaticJavaParser.setConfiguration(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11)
        );
        final Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }
        final List<Path> files;
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .toList();
        }
        for (final Path file : files) {
            transform(file);
        }
    }

    private static void transform(final Path file) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
        LexicalPreservingPrinter.setup(cu);
        removeOldFactoryImports(cu);
        cu.findAll(MethodCallExpr.class).stream()
            .filter(Main::isOldFactoryCall)
            .forEach(Main::rewriteCall);
        Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
    }

    private static void removeOldFactoryImports(final CompilationUnit cu) {
        cu.getImports().removeIf(Main::isOldFactoryImport);
    }

    private static boolean isOldFactoryImport(final ImportDeclaration imp) {
        return imp.getNameAsString().equals(OLD_FACTORY);
    }

    private static boolean isOldFactoryCall(final MethodCallExpr call) {
        if (!"newStorage".equals(call.getNameAsString()) || call.getArguments().size() != 2) {
            return false;
        }
        return call.getScope()
            .filter(ObjectCreationExpr.class::isInstance)
            .map(ObjectCreationExpr.class::cast)
            .map(expr -> isTypeName(expr.getTypeAsString(), OLD_FACTORY, "Storages"))
            .orElse(false);
    }

    private static boolean isTypeName(final String actual, final String fqcn, final String simple) {
        return actual.equals(fqcn) || actual.equals(simple) || actual.endsWith('.' + simple);
    }

    private static void rewriteCall(final MethodCallExpr call) {
        final Expression type = call.getArgument(0);
        final Expression config = call.getArgument(1);
        final ObjectCreationExpr loader = new ObjectCreationExpr(null, StaticJavaParser.parseClassOrInterfaceType(NEW_FACTORY), new NodeList<>());
        final ObjectCreationExpr yamlConfig = new ObjectCreationExpr(
            null,
            StaticJavaParser.parseClassOrInterfaceType(NEW_CONFIG),
            new NodeList<>(config.clone())
        );
        final MethodCallExpr replacement = new MethodCallExpr(loader, "newObject", new NodeList<>(type.clone(), yamlConfig));
        call.replace(replacement);
    }
}
