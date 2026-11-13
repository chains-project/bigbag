package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String TARGET_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String TARGET_OPTIONS = "org.yaml.snakeyaml.LoaderOptions";
    private static final String TARGET_OPTIONS_EXPR = "new " + TARGET_OPTIONS + "()";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        Path root = Paths.get(args[0]);
        List<Path> sources;
        if (Files.isDirectory(root)) {
            try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
                sources = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
            }
        } else {
            sources = List.of(root);
        }

        for (Path source : sources) {
            transform(source);
        }
    }

    private static void transform(Path source) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(cu);
        SnakeyamlConstructorFixer fixer = new SnakeyamlConstructorFixer();
        fixer.visit(cu, null);
        boolean changed = fixer.changed;
        if (changed) {
            Files.writeString(source, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static final class SnakeyamlConstructorFixer extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public ObjectCreationExpr visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            if (!isTargetConstructor(expr)) {
                return expr;
            }
            if (expr.getArguments().size() != 1 || isLoaderOptionsArgument(expr.getArguments().get(0))) {
                return expr;
            }

            Expression loaderOptions = StaticJavaParser.parseExpression(TARGET_OPTIONS_EXPR);
            expr.addArgument(loaderOptions);
            changed = true;
            return expr;
        }

        private boolean isTargetConstructor(ObjectCreationExpr expr) {
            String typeName = expr.getType().asString();
            return TARGET_CONSTRUCTOR.equals(typeName)
                    || simpleName(TARGET_CONSTRUCTOR).equals(typeName)
                    && hasImport(expr.findCompilationUnit().orElse(null), TARGET_CONSTRUCTOR);
        }

        private boolean hasImport(CompilationUnit cu, String fqcn) {
            if (cu == null) {
                return false;
            }
            return cu.getImports().stream().anyMatch(imp -> !imp.isAsterisk() && imp.getNameAsString().equals(fqcn))
                    || cu.getImports().stream().anyMatch(imp -> imp.isAsterisk()
                    && fqcn.startsWith(imp.getNameAsString() + "."));
        }

        private boolean isLoaderOptionsArgument(Expression expression) {
            String text = expression.toString();
            return TARGET_OPTIONS.equals(text) || TARGET_OPTIONS_EXPR.equals(text);
        }

        private String simpleName(String fqcn) {
            int idx = fqcn.lastIndexOf('.') + 1;
            return fqcn.substring(idx);
        }
    }
}
