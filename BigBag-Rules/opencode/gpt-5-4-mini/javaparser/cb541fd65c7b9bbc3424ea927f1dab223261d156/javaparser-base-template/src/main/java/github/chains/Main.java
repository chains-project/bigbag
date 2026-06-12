package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_MATCHERS = "org.hamcrest.Matchers";

    private static final String NEW_MATCHERS = "org.hamcrest.CoreMatchers";

    private static final String OLD_SIMPLE = "Matchers";

    private static final String NEW_SIMPLE = "CoreMatchers";

    private static final Pattern HAMCREST_VERSION = Pattern.compile(
        "<artifactId>hamcrest-(?:core|library)</artifactId>\\s*<version>([^<]+)</version>",
        Pattern.DOTALL
    );

    public static void main(String[] args) {
        final Path root = Paths.get(args.length == 0 ? "." : args[0]);
        try (Stream<Path> files = Files.walk(root)) {
            files.forEach(Main::transform);
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static boolean isJavaFile(final Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }

    private static boolean isPom(final Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().equals("pom.xml");
    }

    private static void transform(final Path file) {
        try {
            if (isPom(file)) {
                transformPom(file);
                return;
            }
            if (!isJavaFile(file)) {
                return;
            }
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            boolean changed = false;
            boolean needsCore = false;
            boolean needsHasProperty = false;
            boolean needsEmptyIterable = false;
            for (final ImportDeclaration imprt : cu.getImports()) {
                if (imprt.getNameAsString().startsWith(OLD_MATCHERS)) {
                    if (imprt.isStatic()) {
                        imprt.remove();
                    } else {
                        imprt.setName(NEW_MATCHERS);
                    }
                    changed = true;
                }
            }
            for (final MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                final String replacement;
                if (call.getNameAsString().equals("hasProperty")
                    || call.getNameAsString().equals("hasPropertyAtPath")) {
                    replacement = "HasPropertyWithValue";
                } else if (call.getNameAsString().equals("emptyIterable")
                    || call.getNameAsString().equals("emptyIterableOf")) {
                    replacement = "IsEmptyIterable";
                } else if (call.getNameAsString().equals("allOf")
                    || call.getNameAsString().equals("any")
                    || call.getNameAsString().equals("anyOf")
                    || call.getNameAsString().equals("anything")
                    || call.getNameAsString().equals("both")
                    || call.getNameAsString().equals("containsString")
                    || call.getNameAsString().equals("containsStringIgnoringCase")
                    || call.getNameAsString().equals("describedAs")
                    || call.getNameAsString().equals("either")
                    || call.getNameAsString().equals("endsWith")
                    || call.getNameAsString().equals("endsWithIgnoringCase")
                    || call.getNameAsString().equals("equalTo")
                    || call.getNameAsString().equals("equalToObject")
                    || call.getNameAsString().equals("everyItem")
                    || call.getNameAsString().equals("hasItem")
                    || call.getNameAsString().equals("hasItems")
                    || call.getNameAsString().equals("hasKey")
                    || call.getNameAsString().equals("hasValue")
                    || call.getNameAsString().equals("instanceOf")
                    || call.getNameAsString().equals("is")
                    || call.getNameAsString().equals("isA")
                    || call.getNameAsString().equals("not")
                    || call.getNameAsString().equals("notNullValue")
                    || call.getNameAsString().equals("nullValue")
                    || call.getNameAsString().equals("sameInstance")
                    || call.getNameAsString().equals("startsWith")
                    || call.getNameAsString().equals("startsWithIgnoringCase")) {
                    replacement = NEW_SIMPLE;
                } else {
                    continue;
                }
                final String scope = call.getScope().map(Object::toString).orElse("");
                if (scope.isEmpty()) {
                    continue;
                }
                if (scope.equals(OLD_SIMPLE) || scope.equals(NEW_SIMPLE)) {
                    call.setScope(new NameExpr(replacement));
                    changed = true;
                    if (replacement.equals(NEW_SIMPLE)) {
                        needsCore = true;
                    } else if (replacement.equals("HasPropertyWithValue")) {
                        needsHasProperty = true;
                    } else if (replacement.equals("IsEmptyIterable")) {
                        needsEmptyIterable = true;
                    }
                }
            }
            if (needsCore && cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_MATCHERS))) {
                cu.addImport(NEW_MATCHERS);
                changed = true;
            }
            if (needsHasProperty && cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("org.hamcrest.beans.HasPropertyWithValue"))) {
                cu.addImport("org.hamcrest.beans.HasPropertyWithValue");
                changed = true;
            }
            if (needsEmptyIterable && cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("org.hamcrest.collection.IsEmptyIterable"))) {
                cu.addImport("org.hamcrest.collection.IsEmptyIterable");
                changed = true;
            }
            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (final Exception err) {
            throw new IllegalStateException(
                String.format("Failed to transform %s", file),
                err
            );
        }
    }

    private static void transformPom(final Path file) throws IOException {
        final String text = Files.readString(file);
        if (text.contains("<artifactId>hamcrest</artifactId>")
            || !(text.contains("<artifactId>hamcrest-core</artifactId>")
                || text.contains("<artifactId>hamcrest-library</artifactId>"))) {
            return;
        }
        final Matcher matcher = HAMCREST_VERSION.matcher(text);
        if (!matcher.find()) {
            return;
        }
        final String dep = String.format(
            "    <dependency>%n"
                + "      <groupId>org.hamcrest</groupId>%n"
                + "      <artifactId>hamcrest</artifactId>%n"
                + "      <version>%s</version>%n"
                + "      <scope>provided</scope>%n"
                + "    </dependency>%n",
            matcher.group(1)
        );
        final String updated = text.replace("</dependencies>", dep + "  </dependencies>");
        Files.writeString(file, updated);
    }
}
