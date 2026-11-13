package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final Set<String> TARGET_TYPES = Set.of(
        "StringContains",
        "StringStartsWith",
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    );

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory path");
        }
        final Path input = Paths.get(args[0]);
        final Path output = args.length > 1 ? Paths.get(args[1]) : input;
        if (!output.equals(input)) {
            copyTree(input, output);
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        addJavaSources(launcher, output);
        launcher.buildModel();
        final List<Replacement> replacements = new ArrayList<>();
        for (CtConstructorCall<?> call : launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        )) {
            if (shouldRewrite(call)) {
                final SourcePosition pos = call.getPosition();
                if (pos != null && pos.isValidPosition() && pos.getFile() != null) {
                    replacements.add(new Replacement(
                        pos.getFile().toPath(),
                        pos.getSourceStart(),
                        pos.getSourceEnd(),
                        rewrite(call)
                    ));
                }
            }
        }
        replacements.sort(Comparator.comparingInt(Replacement::start).reversed());
        for (Replacement replacement : replacements) {
            patchFile(replacement.file(), replacement.start(), replacement.end(), replacement.text());
        }
    }

    private static boolean shouldRewrite(final CtConstructorCall<?> call) {
        return call.getType() != null
            && TARGET_TYPES.contains(call.getType().getQualifiedName())
            && call.getArguments().size() == 2;
    }

    private static String rewrite(final CtConstructorCall<?> call) {
        final String type = call.getType().getSimpleName();
        final String arg = call.getArguments().get(1).toString();
        return "new " + type + "(" + arg + ")";
    }

    private static void patchFile(final Path file, final int start, final int end, final String text) {
        try {
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            final String updated = content.substring(0, start) + text + content.substring(end + 1);
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        } catch (IOException err) {
            throw new IllegalStateException("Failed to patch " + file, err);
        }
    }

    private static void addJavaSources(final Launcher launcher, final Path root) {
        final Path main = root.resolve("src/main/java");
        final Path test = root.resolve("src/test/java");
        if (Files.exists(main)) {
            launcher.addInputResource(main.toString());
        }
        if (Files.exists(test)) {
            launcher.addInputResource(test.toString());
        }
    }

    private static void copyTree(final Path input, final Path output) {
        try (Stream<Path> paths = Files.walk(input)) {
            paths.forEach(path -> {
                try {
                    final Path target = output.resolve(input.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException err) {
                    throw new IllegalStateException("Failed to copy " + path, err);
                }
            });
        } catch (IOException err) {
            throw new IllegalStateException("Failed to copy project tree", err);
        }
    }

    private record Replacement(Path file, int start, int end, String text) {
    }
}
