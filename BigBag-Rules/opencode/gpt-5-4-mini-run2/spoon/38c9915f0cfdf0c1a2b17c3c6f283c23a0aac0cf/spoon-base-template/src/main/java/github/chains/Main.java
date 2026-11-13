package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon rewrite for Cactoos 0.55.0 API renames.
 */
public final class Main {

    private static final Map<String, String> REWRITES = new LinkedHashMap<>();

    static {
        REWRITES.put("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered");
        REWRITES.put("org.cactoos.collection.CollectionOf", "org.cactoos.collection.Immutable");
        REWRITES.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        REWRITES.put("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked");
        REWRITES.put("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked");
        REWRITES.put("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid");
        REWRITES.put("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky");
        REWRITES.put("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked");
        REWRITES.put("org.cactoos.text.JoinedText", "org.cactoos.text.Joined");
        REWRITES.put("org.cactoos.text.RandomText", "org.cactoos.text.Randomized");
        REWRITES.put("org.cactoos.text.SplitText", "org.cactoos.text.Split");
        REWRITES.put("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed");
    }

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }
        final Path input = Paths.get(args[0]);
        final Path output = args.length > 1
            ? Paths.get(args[1])
            : input.resolveSibling(input.getFileName() + "-spooned");
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(ref -> rewrite(ref));
        launcher.getModel().getElements(new TypeFilter<>(CtElement.class)).forEach(el -> {
            // force model traversal so all imports and references are materialized before printing
        });
        launcher.prettyprint();
    }

    private static void rewrite(final CtTypeReference<?> ref) {
        final String replacement = REWRITES.get(ref.getQualifiedName());
        if (replacement != null) {
            ref.replace(ref.getFactory().createReference(replacement));
        }
    }
}
