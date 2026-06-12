package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class Main {

    private static final Set<String> TARGET_TYPES = Set.of(
        "StringContains",
        "StringStartsWith"
    );

    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        final JavaParser parser = new JavaParser(new ParserConfiguration());
        try {
            Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> transformFile(parser, path));
            Files.walk(root)
                .filter(path -> path.getFileName() != null && "pom.xml".equals(path.getFileName().toString()))
                .forEach(Main::transformPom);
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    private static void transformFile(final JavaParser parser, final Path path) {
        try {
            final ParseResult<CompilationUnit> parsed = parser.parse(path);
            if (!parsed.isSuccessful() || !parsed.getResult().isPresent()) {
                return;
            }
            final CompilationUnit cu = parsed.getResult().get();
            boolean changed = false;
            for (final ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                if (isTargetConstructor(expr) && expr.getArguments().size() == 2) {
                    final Expression expected = expr.getArgument(1).clone();
                    expr.getArguments().clear();
                    expr.addArgument(expected);
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    private static boolean isTargetConstructor(final ObjectCreationExpr expr) {
        return TARGET_TYPES.contains(expr.getType().getNameAsString());
    }

    private static void transformPom(final Path path) {
        try {
            String pom = Files.readString(path, StandardCharsets.UTF_8);
            final String dependency = "<dependency>\n"
                + "      <groupId>com.artipie</groupId>\n"
                + "      <artifactId>asto-core</artifactId>\n"
                + "      <version>v1.15.4</version>\n"
                + "    </dependency>";
            final String replacement = "<dependency>\n"
                + "      <groupId>com.artipie</groupId>\n"
                + "      <artifactId>asto-core</artifactId>\n"
                + "      <version>v1.15.4</version>\n"
                + "      <exclusions>\n"
                + "        <exclusion>\n"
                + "          <groupId>org.hamcrest</groupId>\n"
                + "          <artifactId>hamcrest-core</artifactId>\n"
                + "        </exclusion>\n"
                + "      </exclusions>\n"
                + "    </dependency>";
            if (pom.contains(dependency) && !pom.contains("<artifactId>hamcrest-core</artifactId>")) {
                pom = pom.replace(dependency, replacement);
                Files.writeString(path, pom, StandardCharsets.UTF_8);
            }
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }
}
