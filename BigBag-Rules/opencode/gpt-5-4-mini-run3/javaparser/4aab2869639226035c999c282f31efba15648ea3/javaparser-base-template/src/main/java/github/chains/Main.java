package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public final class Main {

    private Main() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }
        final Path root = Paths.get(args[0]);
        final List<Path> files = Files.walk(root)
            .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        for (final Path file : files) {
            transform(file);
        }
    }

    private static void transform(final Path file) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
        LexicalPreservingPrinter.setup(unit);
        unit.getImports().removeIf(imp -> imp.getNameAsString().equals("com.artipie.asto.factory.Storages"));
        final boolean[] changed = {false};
        unit.findAll(MethodCallExpr.class).forEach(call -> {
            if (!"newStorage".equals(call.getNameAsString()) || call.getArguments().size() != 2) {
                return;
            }
            if (call.getScope().isEmpty()) {
                return;
            }
            final String scopeText = call.getScope().get().toString();
            if (!scopeText.endsWith("Storages()") && !scopeText.endsWith("Storages")) {
                return;
            }
            final Expression typeArg = call.getArgument(0).clone();
            final Expression cfgArg = call.getArgument(1).clone();
            final ObjectCreationExpr loader = new ObjectCreationExpr(
                null,
                new ClassOrInterfaceType(null, "com.artipie.asto.factory.StoragesLoader"),
                new NodeList<>()
            );
            final ObjectCreationExpr config = new ObjectCreationExpr(
                null,
                new ClassOrInterfaceType(null, "com.artipie.asto.factory.Config.YamlStorageConfig"),
                NodeList.nodeList(cfgArg)
            );
            final MethodCallExpr replacement = new MethodCallExpr(
                loader,
                new SimpleName("newObject"),
                NodeList.nodeList(typeArg, config)
            );
            call.replace(replacement);
            changed[0] = true;
        });
        if (changed[0]) {
            Files.writeString(file, LexicalPreservingPrinter.print(unit), StandardCharsets.UTF_8);
        }
    }
}
