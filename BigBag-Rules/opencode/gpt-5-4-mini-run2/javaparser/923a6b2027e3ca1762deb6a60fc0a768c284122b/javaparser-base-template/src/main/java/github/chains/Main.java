package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class Main {

    private static final String OLD_IMPORT = "org.hamcrest.Matchers";

    private static final String NEW_IMPORT = "org.hamcrest.CoreMatchers";

    private static final Set<String> MOVED_METHODS = new HashSet<>();

    static {
        MOVED_METHODS.add("hasEntry");
        MOVED_METHODS.add("hasKey");
        MOVED_METHODS.add("hasValue");
        MOVED_METHODS.add("contains");
        MOVED_METHODS.add("containsInAnyOrder");
        MOVED_METHODS.add("containsInRelativeOrder");
        MOVED_METHODS.add("arrayContaining");
        MOVED_METHODS.add("arrayContainingInAnyOrder");
        MOVED_METHODS.add("arrayWithSize");
        MOVED_METHODS.add("emptyArray");
        MOVED_METHODS.add("aMapWithSize");
        MOVED_METHODS.add("anEmptyMap");
        MOVED_METHODS.add("hasSize");
        MOVED_METHODS.add("empty");
        MOVED_METHODS.add("emptyCollectionOf");
        MOVED_METHODS.add("emptyIterable");
        MOVED_METHODS.add("emptyIterableOf");
        MOVED_METHODS.add("iterableWithSize");
        MOVED_METHODS.add("hasProperty");
        MOVED_METHODS.add("samePropertyValuesAs");
        MOVED_METHODS.add("hasXPath");
        MOVED_METHODS.add("hasToString");
        MOVED_METHODS.add("typeCompatibleWith");
        MOVED_METHODS.add("eventFrom");
        MOVED_METHODS.add("blankOrNullString");
        MOVED_METHODS.add("blankString");
        MOVED_METHODS.add("emptyOrNullString");
        MOVED_METHODS.add("emptyString");
        MOVED_METHODS.add("isEmptyOrNullString");
        MOVED_METHODS.add("isEmptyString");
        MOVED_METHODS.add("containsString");
        MOVED_METHODS.add("containsStringIgnoringCase");
        MOVED_METHODS.add("startsWith");
        MOVED_METHODS.add("startsWithIgnoringCase");
        MOVED_METHODS.add("endsWith");
        MOVED_METHODS.add("endsWithIgnoringCase");
        MOVED_METHODS.add("matchesPattern");
        MOVED_METHODS.add("matchesRegex");
        MOVED_METHODS.add("allOf");
        MOVED_METHODS.add("anyOf");
        MOVED_METHODS.add("both");
        MOVED_METHODS.add("either");
        MOVED_METHODS.add("describedAs");
        MOVED_METHODS.add("everyItem");
        MOVED_METHODS.add("is");
        MOVED_METHODS.add("isA");
        MOVED_METHODS.add("anything");
        MOVED_METHODS.add("hasItem");
        MOVED_METHODS.add("hasItems");
        MOVED_METHODS.add("equalTo");
        MOVED_METHODS.add("equalToObject");
        MOVED_METHODS.add("any");
        MOVED_METHODS.add("instanceOf");
        MOVED_METHODS.add("not");
        MOVED_METHODS.add("notNullValue");
        MOVED_METHODS.add("nullValue");
        MOVED_METHODS.add("sameInstance");
        MOVED_METHODS.add("theInstance");
        MOVED_METHODS.add("closeTo");
        MOVED_METHODS.add("notANumber");
        MOVED_METHODS.add("comparesEqualTo");
        MOVED_METHODS.add("greaterThan");
        MOVED_METHODS.add("greaterThanOrEqualTo");
        MOVED_METHODS.add("lessThan");
        MOVED_METHODS.add("lessThanOrEqualTo");
        MOVED_METHODS.add("equalToIgnoringCase");
        MOVED_METHODS.add("equalToIgnoringWhiteSpace");
        MOVED_METHODS.add("equalToCompressingWhiteSpace");
        MOVED_METHODS.add("hasLength");
        MOVED_METHODS.add("hasXPath");
        MOVED_METHODS.add("hasEntry");
    }

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        final JavaParser parser = new JavaParser(new ParserConfiguration());
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> rewrite(parser, path));
        }
    }

    private static void rewrite(final JavaParser parser, final Path path) {
        try {
            final CompilationUnit unit = parser.parse(path).getResult()
                .orElseThrow(() -> new IllegalStateException(path.toString()));
            LexicalPreservingPrinter.setup(unit);
            final RewriteVisitor visitor = new RewriteVisitor();
            final Visitable visited = visitor.visit(unit, null);
            if (visitor.changed) {
                final CompilationUnit rewritten = (CompilationUnit) visited;
                Files.write(
                    path,
                    LexicalPreservingPrinter.print(rewritten)
                        .getBytes(StandardCharsets.UTF_8)
                );
            }
        } catch (final Exception err) {
            throw new IllegalStateException(path.toString(), err);
        }
    }

    private static final class RewriteVisitor
        extends ModifierVisitor<Void> {

        private boolean changed;

        @Override
        public Visitable visit(final CompilationUnit unit, final Void arg) {
            for (final ImportDeclaration imp : unit.getImports()) {
                if (imp.getNameAsString().equals(OLD_IMPORT)) {
                    imp.setName(NEW_IMPORT);
                    this.changed = true;
                }
            }
            return super.visit(unit, arg);
        }

        @Override
        public Visitable visit(final MethodCallExpr call, final Void arg) {
            final Visitable result = super.visit(call, arg);
            if (call.getScope().isPresent()) {
                final Node scope = call.getScope().get();
                if (scope instanceof NameExpr) {
                    final NameExpr name = (NameExpr) scope;
                    if (name.getNameAsString().equals("Matchers")
                        && MOVED_METHODS.contains(call.getNameAsString())) {
                        name.setName("CoreMatchers");
                        this.changed = true;
                    }
                }
            }
            return result;
        }
    }
}
