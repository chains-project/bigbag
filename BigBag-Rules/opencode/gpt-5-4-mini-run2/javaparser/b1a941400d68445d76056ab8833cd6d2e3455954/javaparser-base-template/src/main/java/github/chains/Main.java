package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {

    private static final String OLD_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String YAML_TYPE = "org.yaml.snakeyaml.Yaml";
    private static final String CONSTRUCTOR_TYPE = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String LOADER_OPTIONS_TYPE = "org.yaml.snakeyaml.LoaderOptions";
    private static final String TRUSTED_INSPECTOR_TYPE = "org.yaml.snakeyaml.inspector.TrustedTagInspector";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }

        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewriteFile);
    }

    private static void rewriteFile(Path path) {
        try {
            String original = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(original);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = transformConstructorCalls(cu);

            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformConstructorCalls(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if (isLoaderOptions(expr) && expr.getArguments().isEmpty()) {
                expr.replace(StaticJavaParser.parseExpression(trustedLoaderOptionsExpression()));
                changed = true;
                continue;
            }
            if (expr.getArguments().isEmpty() && isYamlOrConstructor(expr)) {
                NodeList<Expression> args = new NodeList<>();
                args.add(StaticJavaParser.parseExpression(trustedLoaderOptionsExpression()));
                expr.setArguments(args);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isLoaderOptions(ObjectCreationExpr expr) {
        String typeName = expr.getType().asString();
        return LOADER_OPTIONS_TYPE.equals(typeName) || "LoaderOptions".equals(typeName) || typeName.endsWith(".LoaderOptions");
    }

    private static boolean isYamlOrConstructor(ObjectCreationExpr expr) {
        String typeName = expr.getType().asString();
        return YAML_TYPE.equals(typeName) || CONSTRUCTOR_TYPE.equals(typeName)
                || "Yaml".equals(typeName) || "Constructor".equals(typeName)
                || typeName.endsWith(".Yaml") || typeName.endsWith(".Constructor");
    }

    private static String trustedLoaderOptionsExpression() {
        return "((java.util.function.Supplier<org.yaml.snakeyaml.LoaderOptions>) () -> {"
                + " org.yaml.snakeyaml.LoaderOptions options = new org.yaml.snakeyaml.LoaderOptions();"
                + " options.setTagInspector(new " + TRUSTED_INSPECTOR_TYPE + "());"
                + " return options;"
                + " }).get()";
    }
}
