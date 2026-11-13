package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class Main {
    private static final String HAMCREST = "org.hamcrest";
    private static final String CORE_MATCHERS = "org.hamcrest.CoreMatchers";
    private static final String COLLECTION_MATCHERS = "org.hamcrest.collection";
    private static final String BEAN_MATCHERS = "org.hamcrest.beans";

    private Main() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path sourceRoot = Paths.get(args[0]);
        final SourceRoot source = new SourceRoot(sourceRoot, new ParserConfiguration());
        source.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        source.parseParallelized("", new SourceRoot.Callback() {
            @Override
            public SourceRoot.Callback.Result process(final Path localPath, final Path absolutePath,
                final com.github.javaparser.ParseResult<CompilationUnit> result) {
                if (!result.getResult().isPresent()) {
                    return SourceRoot.Callback.Result.DONT_SAVE;
                }
                final CompilationUnit unit = result.getResult().get();
                LexicalPreservingPrinter.setup(unit);
                final ImportIndex imports = new ImportIndex(unit);
                final RewriteVisitor visitor = new RewriteVisitor(imports);
                final boolean[] changed = {false};
                unit.accept(visitor, changed);
                if (changed[0] || visitor.changed) {
                    imports.apply(unit);
                    try {
                        Files.writeString(absolutePath, LexicalPreservingPrinter.print(unit));
                    } catch (final IOException err) {
                        throw new IllegalStateException(err);
                    }
                }
                return SourceRoot.Callback.Result.DONT_SAVE;
            }
        });
    }

    private static final class RewriteVisitor extends ModifierVisitor<boolean[]> {
        private final ImportIndex imports;
        private boolean changed;

        RewriteVisitor(final ImportIndex imports) {
            this.imports = imports;
        }

        @Override
        public Visitable visit(final MethodCallExpr expr, final boolean[] changed) {
            super.visit(expr, changed);
            final Optional<String> owner = this.imports.resolve(expr.getScope());
            if (!owner.isPresent() || !owner.get().startsWith(HAMCREST)) {
                return expr;
            }
            final String target = targetOwner(expr.getNameAsString());
            if (target == null) {
                return expr;
            }
            expr.setScope(new NameExpr(this.imports.ensureImported(target)));
            this.changed = true;
            changed[0] = true;
            return expr;
        }

        @Override
        public Visitable visit(final ObjectCreationExpr expr, final boolean[] changed) {
            super.visit(expr, changed);
            final Optional<String> owner = this.imports.resolve(expr.getScope());
            if (owner.isPresent() && owner.get().startsWith(HAMCREST)) {
                final String simple = expr.getType().getNameAsString();
                if ("Matchers".equals(simple) || "CoreMatchers".equals(simple)) {
                    expr.removeScope();
                    this.changed = true;
                    changed[0] = true;
                }
            }
            return expr;
        }

        private String targetOwner(final String method) {
            switch (method) {
                case "hasItem":
                case "hasItems":
                case "contains":
                case "containsInAnyOrder":
                case "containsInRelativeOrder":
                case "iterableWithSize":
                case "emptyIterable":
                case "emptyIterableOf":
                    return COLLECTION_MATCHERS + ".IsCollectionContaining";
                case "hasEntry":
                case "hasKey":
                case "hasValue":
                    return COLLECTION_MATCHERS + ".IsMapContaining";
                case "empty":
                case "emptyCollectionOf":
                    return COLLECTION_MATCHERS + ".IsEmptyCollection";
                case "containsString":
                case "containsStringIgnoringCase":
                    return CORE_MATCHERS + ".StringContains";
                case "startsWith":
                case "startsWithIgnoringCase":
                    return CORE_MATCHERS + ".StringStartsWith";
                case "endsWith":
                case "endsWithIgnoringCase":
                    return CORE_MATCHERS + ".StringEndsWith";
                case "equalTo":
                case "equalToObject":
                    return CORE_MATCHERS + ".IsEqual";
                case "not":
                    return CORE_MATCHERS + ".IsNot";
                case "notNullValue":
                    return CORE_MATCHERS + ".IsNull";
                case "any":
                    return CORE_MATCHERS + ".IsAnything";
                case "allOf":
                case "is":
                case "describedAs":
                case "hasToString":
                case "hasSize":
                    return CORE_MATCHERS;
                case "hasProperty":
                    return BEAN_MATCHERS + ".HasPropertyWithValue";
                default:
                    return null;
            }
        }
    }

    private static final class ImportIndex {
        private final Map<String, String> imports = new HashMap<>();

        ImportIndex(final CompilationUnit unit) {
            unit.getImports().forEach(decl -> {
                if (!decl.isAsterisk()) {
                    this.imports.put(simpleName(decl.getNameAsString()), decl.getNameAsString());
                }
            });
        }

        Optional<String> resolve(final Optional<Expression> scope) {
            if (!scope.isPresent()) {
                return Optional.empty();
            }
            final Expression expr = scope.get();
            if (expr.isNameExpr()) {
                final String name = expr.asNameExpr().getNameAsString();
                return Optional.of(this.imports.getOrDefault(name, name));
            }
            return Optional.of(expr.toString());
        }

        String ensureImported(final String fqcn) {
            final String simple = simpleName(fqcn);
            this.imports.put(simple, fqcn);
            return simple;
        }

        void apply(final CompilationUnit unit) {
            for (final Map.Entry<String, String> entry : this.imports.entrySet()) {
                if (entry.getValue().startsWith(CORE_MATCHERS)
                    || entry.getValue().startsWith(COLLECTION_MATCHERS)
                    || entry.getValue().startsWith(BEAN_MATCHERS)) {
                    unit.addImport(entry.getValue());
                }
            }
        }
    }

    private static String simpleName(final String fqcn) {
        final int idx = fqcn.lastIndexOf('.');
        return idx < 0 ? fqcn : fqcn.substring(idx + 1);
    }
}
