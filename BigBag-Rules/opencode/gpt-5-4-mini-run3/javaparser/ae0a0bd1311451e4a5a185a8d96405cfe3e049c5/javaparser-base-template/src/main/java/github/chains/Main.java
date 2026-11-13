package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Main {

    private static final Set<String> TARGET_TYPES = new HashSet<>();

    static {
        TARGET_TYPES.add("org.hamcrest.core.StringContains");
        TARGET_TYPES.add("org.hamcrest.core.StringStartsWith");
        TARGET_TYPES.add("org.hamcrest.core.StringEndsWith");
        TARGET_TYPES.add("StringContains");
        TARGET_TYPES.add("StringStartsWith");
        TARGET_TYPES.add("StringEndsWith");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }
        final Path root = Paths.get(args[0]);
        final JavaParser parser = new JavaParser(new ParserConfiguration());
        final MatcherFixer fixer = new MatcherFixer();
        Files.walk(root)
            .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
            .forEach(path -> rewrite(parser, fixer, path));
    }

    private static void rewrite(final JavaParser parser, final MatcherFixer fixer, final Path path) {
        try {
            final CompilationUnit cu = parser.parse(path).getResult().orElse(null);
            if (cu == null) {
                return;
            }
            LexicalPreservingPrinter.setup(cu);
            cu.accept(fixer, null);
            final boolean changed = fixer.changed;
            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception err) {
            throw new IllegalStateException("Failed to rewrite " + path, err);
        }
    }

    private static final class MatcherFixer extends VoidVisitorAdapter<Void> {

        private boolean changed;

        @Override
        public void visit(final ObjectCreationExpr expr, final Void arg) {
            super.visit(expr, arg);
            final String type = expr.getType().asString();
            if (TARGET_TYPES.contains(type) && expr.getArguments().size() == 2) {
                expr.getArguments().remove(0);
                this.changed = true;
            }
        }
    }
}
