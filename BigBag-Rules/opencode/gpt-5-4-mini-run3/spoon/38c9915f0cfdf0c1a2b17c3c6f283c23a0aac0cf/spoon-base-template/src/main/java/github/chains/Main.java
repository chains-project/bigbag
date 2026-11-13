package github.chains;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final Map<String, String> RENAMES = Map.ofEntries(
        Map.entry("org.cactoos.collection.CollectionOf", "org.cactoos.list.ListOf"),
        Map.entry("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered"),
        Map.entry("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf"),
        Map.entry("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked"),
        Map.entry("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked"),
        Map.entry("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid"),
        Map.entry("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky"),
        Map.entry("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked"),
        Map.entry("org.cactoos.text.JoinedText", "org.cactoos.text.Joined"),
        Map.entry("org.cactoos.text.RandomText", "org.cactoos.text.Randomized"),
        Map.entry("org.cactoos.text.SplitText", "org.cactoos.text.Split"),
        Map.entry("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed")
    );

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }
        final Path source = Path.of(args[0]);
        final Path target = args.length > 1 ? Path.of(args[1]) : source;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(source.toString());
        launcher.setSourceOutputDirectory(target.toFile());
        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(final CtTypeReference<?> reference) {
                final String replacement = RENAMES.get(reference.getQualifiedName());
                if (replacement == null) {
                    return;
                }
                final CtTypeReference<?> updated = reference.getFactory().Type().createReference(replacement);
                updated.setActualTypeArguments(List.copyOf(reference.getActualTypeArguments()));
                reference.replace(updated);
            }
        });
        launcher.run();
    }
}
