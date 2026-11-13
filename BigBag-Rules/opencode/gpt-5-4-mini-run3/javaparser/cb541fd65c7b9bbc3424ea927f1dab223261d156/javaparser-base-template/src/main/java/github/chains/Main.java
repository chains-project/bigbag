package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {

    private static final String OLD_IMPORT = "import org.hamcrest.Matchers;";

    private static final String NEW_IMPORT = "import org.hamcrest.CoreMatchers;";

    public static void main(String[] args) {
        final Path root = Paths.get(args.length == 0 ? "." : args[0]);
        final ParserConfiguration config = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        final JavaParser parser = new JavaParser(config);
        try {
            Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> rewrite(parser, path));
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static void rewrite(final JavaParser parser, final Path path) {
        try {
            final ParseResult<CompilationUnit> parsed = parser.parse(path);
            final Optional<CompilationUnit> maybe = parsed.getResult();
            if (!maybe.isPresent()) {
                return;
            }
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            text = text.replace("CoreCoreMatchers.", "CoreMatchers.");
            text = text.replace("Coreorg.hamcrest.collection.", "org.hamcrest.collection.");
            text = text.replace("Coreorg.hamcrest.beans.", "org.hamcrest.beans.");
            text = text.replace(OLD_IMPORT, NEW_IMPORT);
            text = text.replace("Matchers.hasProperty(", "org.hamcrest.beans.HasPropertyWithValue.hasProperty(");
            text = text.replace("Matchers.emptyIterableOf(", "org.hamcrest.collection.IsEmptyIterable.emptyIterableOf(");
            text = text.replace("Matchers.emptyIterable(", "org.hamcrest.collection.IsEmptyIterable.emptyIterable(");
            text = text.replace("Matchers.hasItems(", "CoreMatchers.hasItems(");
            text = text.replace("Matchers.hasItem(", "CoreMatchers.hasItem(");
            text = text.replace("Matchers.contains(", "CoreMatchers.contains(");
            text = text.replace("Matchers.not(", "CoreMatchers.not(");
            text = text.replace("Matchers.containsString(", "CoreMatchers.containsString(");
            text = text.replace("Matchers.startsWith(", "CoreMatchers.startsWith(");
            text = text.replace("Matchers.equalTo(", "CoreMatchers.equalTo(");
            text = text.replace("Matchers.notNullValue(", "CoreMatchers.notNullValue(");
            text = text.replace("Matchers.emptyIterableOf(", "org.hamcrest.collection.IsEmptyIterable.emptyIterableOf(");
            text = text.replace("Matchers.emptyIterable(", "org.hamcrest.collection.IsEmptyIterable.emptyIterable(");
            text = text.replace("CoreMatchers.<URI>hasProperty(", "org.hamcrest.beans.HasPropertyWithValue.hasProperty(");
            if (!text.equals(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))) {
                Files.write(path, text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }
}
