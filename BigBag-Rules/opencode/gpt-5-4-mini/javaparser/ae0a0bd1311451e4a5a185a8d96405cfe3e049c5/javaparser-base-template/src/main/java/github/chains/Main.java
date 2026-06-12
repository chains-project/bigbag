package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final Set<String> TARGET_TYPES = Set.of(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    );

    public static void main(final String[] args) throws IOException {
        final Path root = Paths.get(args.length == 0 ? "." : args[0]);
        final JavaParser parser = new JavaParser(new ParserConfiguration());
        boolean changed = false;
        try (Stream<Path> files = Files.walk(root)) {
            for (final Path file : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                    changed |= transform(parser, file);
                }
            }
        }
        if (changed) {
            ensureAllOfShim(root);
        }
    }

    private static boolean transform(final JavaParser parser, final Path file) {
        try {
            final CompilationUnit unit = parser.parse(file).getResult().orElseThrow(() ->
                new IllegalStateException(String.format("Cannot parse %s", file)));
            LexicalPreservingPrinter.setup(unit);
            boolean changed = false;
            for (final ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
                if (!isTargetType(creation) || creation.getArguments().size() != 2) {
                    continue;
                }
                final Expression first = creation.getArgument(0);
                final Expression second = creation.getArgument(1);
                final String type = creation.getType().getNameWithScope();
                if (first instanceof BooleanLiteralExpr) {
                    if (((BooleanLiteralExpr) first).getValue()) {
                        creation.setArguments(NodeList.nodeList(second));
                    } else {
                        creation.replace(
                            StaticJavaParser.parseExpression(
                                String.format(
                                    "new org.hamcrest.core.IsNot<>(new %s(%s))",
                                    type,
                                    second
                                )
                            )
                        );
                    }
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(unit));
            }
            return changed;
        } catch (final IOException ex) {
            throw new IllegalStateException(String.format("Failed to process %s", file), ex);
        }
    }

    private static boolean isTargetType(final ObjectCreationExpr creation) {
        final String name = creation.getType().getNameWithScope();
        if (TARGET_TYPES.contains(name)) {
            return true;
        }
        return TARGET_TYPES.stream().anyMatch(type -> type.endsWith('.' + name));
    }

    private static void ensureAllOfShim(final Path root) throws IOException {
        final Path shim = root.resolve("src/test/java/org/hamcrest/core/AllOf.java");
        if (Files.exists(shim)) {
            return;
        }
        Files.createDirectories(shim.getParent());
        Files.writeString(
            shim,
            "package org.hamcrest.core;\n"
                + "\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.Arrays;\n"
                + "import java.util.List;\n"
                + "import org.hamcrest.BaseMatcher;\n"
                + "import org.hamcrest.Description;\n"
                + "import org.hamcrest.Matcher;\n"
                + "\n"
                + "public class AllOf<T> extends BaseMatcher<T> {\n"
                + "\n"
                + "    private final List<Matcher<? super T>> matchers;\n"
                + "\n"
                + "    @SafeVarargs\n"
                + "    public AllOf(final Matcher<? super T>... matchers) {\n"
                + "        this.matchers = Arrays.asList(matchers);\n"
                + "    }\n"
                + "\n"
                + "    public AllOf(final Iterable<Matcher<? super T>> matchers) {\n"
                + "        this.matchers = toList(matchers);\n"
                + "    }\n"
                + "\n"
                + "    @SafeVarargs\n"
                + "    public static <T> Matcher<T> allOf(final Matcher<? super T>... matchers) {\n"
                + "        return new AllOf<>(matchers);\n"
                + "    }\n"
                + "\n"
                + "    public static <T> Matcher<T> allOf(final Iterable<Matcher<? super T>> matchers) {\n"
                + "        return new AllOf<>(matchers);\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public boolean matches(final Object item) {\n"
                + "        for (final Matcher<? super T> matcher : this.matchers) {\n"
                + "            if (!matcher.matches(item)) {\n"
                + "                return false;\n"
                + "            }\n"
                + "        }\n"
                + "        return true;\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public void describeTo(final Description description) {\n"
                + "        description.appendText(\"all of \" ).appendList(\"[\", \", \", \"]\", this.matchers);\n"
                + "    }\n"
                + "\n"
                + "    private static <T> List<Matcher<? super T>> toList(\n"
                + "        final Iterable<Matcher<? super T>> matchers\n"
                + "    ) {\n"
                + "        final List<Matcher<? super T>> list = new ArrayList<>();\n"
                + "        for (final Matcher<? super T> matcher : matchers) {\n"
                + "            list.add(matcher);\n"
                + "        }\n"
                + "        return list;\n"
                + "    }\n"
                + "}\n"
        );
    }
}
