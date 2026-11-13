package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class Main {
    private static final Map<String, String> IMPORTS = new LinkedHashMap<>();

    private static final Map<String, String> TYPES = new LinkedHashMap<>();

    static {
        IMPORTS.put("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered");
        IMPORTS.put("org.cactoos.collection.CollectionOf", "org.cactoos.list.ListOf");
        IMPORTS.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        IMPORTS.put("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked");
        IMPORTS.put("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked");
        IMPORTS.put("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid");
        IMPORTS.put("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky");
        IMPORTS.put("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked");
        IMPORTS.put("org.cactoos.text.JoinedText", "org.cactoos.text.Joined");
        IMPORTS.put("org.cactoos.text.RandomText", "org.cactoos.text.Randomized");
        IMPORTS.put("org.cactoos.text.SplitText", "org.cactoos.text.Split");
        IMPORTS.put("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed");

        IMPORTS.forEach((oldName, newName) -> TYPES.put(simple(oldName), simple(newName)));
    }

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected a source directory path");
        }
        final Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(
                ParserConfiguration.LanguageLevel.JAVA_11
            )
        );
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        transform(parser, path);
                    } catch (final IOException err) {
                        throw new IllegalStateException(err);
                    }
                });
        }
    }

    private static void transform(final JavaParser parser, final Path path)
        throws IOException {
        final ParseResult<CompilationUnit> parsed = parser.parse(path);
        if (parsed.getResult().isEmpty()) {
            return;
        }
        final CompilationUnit cu = parsed.getResult().get();
        LexicalPreservingPrinter.setup(cu);
        final AtomicBoolean changed = new AtomicBoolean(false);
        cu.getImports().forEach(imp -> {
            final String name = imp.getNameAsString();
            final String updated = IMPORTS.get(name);
            if (updated != null) {
                imp.setName(updated);
                changed.set(true);
            }
        });
        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            final String updated = TYPES.get(type.getNameAsString());
            if (updated != null) {
                type.setName(updated);
                changed.set(true);
            }
        });
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            final String updated = TYPES.get(expr.getType().getNameAsString());
            if (updated != null) {
                expr.setType(updated);
                changed.set(true);
            }
        });
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if ("intValue".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                final Expression scope = call.getScope().get();
                if (scope.isObjectCreationExpr() && "LengthOf".equals(scope.asObjectCreationExpr().getType().getNameAsString())) {
                    call.setName("value");
                    changed.set(true);
                }
            }
            if ("isEmpty".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                final Expression scope = call.getScope().get();
                if (scope.isObjectCreationExpr() && "Filtered".equals(scope.asObjectCreationExpr().getType().getNameAsString())) {
                    final ObjectCreationExpr wrapper = new ObjectCreationExpr();
                    wrapper.setType(new ClassOrInterfaceType(null, "ListOf"));
                    wrapper.setArguments(new NodeList<>(scope.clone()));
                    call.setScope(wrapper);
                    changed.set(true);
                }
            }
        });
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            if ("Skipped".equals(expr.getType().getNameAsString()) && expr.getArguments().size() == 2) {
                final Expression first = expr.getArgument(0);
                final Expression second = expr.getArgument(1);
                if (!first.isIntegerLiteralExpr() && second.isIntegerLiteralExpr()) {
                    expr.setArgument(0, second.clone());
                    expr.setArgument(1, first.clone());
                    changed.set(true);
                }
            }
            if ("IterableOf".equals(expr.getType().getNameAsString()) && expr.getArguments().size() == 1) {
                final Expression arg = expr.getArgument(0);
                if (arg.isNameExpr() && shouldUseListOf(cu, arg.asNameExpr().getNameAsString())) {
                    expr.setType("ListOf");
                    changed.set(true);
                }
            }
        });
        if (changed.get()) {
            String source = LexicalPreservingPrinter.print(cu);
            final var replacements = new ArrayList<>(TYPES.entrySet());
            replacements.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
            for (final Map.Entry<String, String> entry : replacements) {
                source = source.replace(entry.getKey(), entry.getValue());
            }
            source = source.replace(".intValue()", ".value()");
            Files.writeString(path, source);
        }
    }

    private static boolean shouldUseListOf(final CompilationUnit cu, final String name) {
        for (final VariableDeclarator var : cu.findAll(VariableDeclarator.class)) {
            if (name.equals(var.getNameAsString())) {
                return isIterableLike(var.getType().asString());
            }
        }
        for (final Parameter param : cu.findAll(Parameter.class)) {
            if (name.equals(param.getNameAsString())) {
                return isIterableLike(param.getType().asString());
            }
        }
        for (final FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            for (final VariableDeclarator var : field.getVariables()) {
                if (name.equals(var.getNameAsString())) {
                    return isIterableLike(var.getType().asString());
                }
            }
        }
        return false;
    }

    private static boolean isIterableLike(final String type) {
        return type.contains("List") || type.contains("Collection") || type.contains("Iterable");
    }

    private static String simple(final String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }
}
