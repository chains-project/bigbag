package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

public final class Main {

    private static final String OLD_FACTORY = "com.artipie.asto.factory.Storages";

    private static final String NEW_FACTORY = "com.artipie.asto.factory.StoragesLoader";

    private static final String CONFIG = "com.artipie.asto.factory.Config";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transform);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            final boolean[] changed = {false};
            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (isOldFactoryCall(call)) {
                    call.replace(rewrite(call));
                    changed[0] = true;
                }
            });
            if (changed[0]) {
                ensureImport(cu, NEW_FACTORY);
                ensureImport(cu, CONFIG);
                removeImport(cu, OLD_FACTORY);
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (final Exception err) {
            throw new IllegalStateException(String.format("Failed to transform %s", file), err);
        }
    }

    private static boolean isOldFactoryCall(final MethodCallExpr call) {
        return "newStorage".equals(call.getNameAsString())
            && call.getArguments().size() == 2
            && call.getScope().map(Main::isOldFactoryCreation).orElse(false);
    }

    private static boolean isOldFactoryCreation(final Expression expr) {
        return expr.isObjectCreationExpr()
            && expr.asObjectCreationExpr().getTypeAsString().endsWith("Storages");
    }

    private static MethodCallExpr rewrite(final MethodCallExpr call) {
        final Expression type = call.getArgument(0).clone();
        final Expression cfg = call.getArgument(1).clone();
        final ObjectCreationExpr loader = new ObjectCreationExpr(
            null,
            StaticJavaParser.parseClassOrInterfaceType("StoragesLoader"),
            new NodeList<>()
        );
        final ObjectCreationExpr wrapped = new ObjectCreationExpr(
            null,
            StaticJavaParser.parseClassOrInterfaceType("Config.YamlStorageConfig"),
            NodeList.nodeList(cfg)
        );
        return new MethodCallExpr(loader, "newObject", NodeList.nodeList(type, wrapped));
    }

    private static void ensureImport(final CompilationUnit cu, final String type) {
        if (cu.getImports().stream().noneMatch(imp -> imp.getNameAsString().equals(type))) {
            cu.addImport(type);
        }
    }

    private static void removeImport(final CompilationUnit cu, final String type) {
        cu.getImports().stream()
            .filter(imp -> imp.getNameAsString().equals(type))
            .findFirst()
            .ifPresent(imp -> cu.remove(imp));
    }
}
