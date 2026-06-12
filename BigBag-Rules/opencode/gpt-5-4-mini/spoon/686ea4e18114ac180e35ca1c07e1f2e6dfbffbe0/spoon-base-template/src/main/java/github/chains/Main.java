package github.chains;

import spoon.Launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final List<PrefixMapping> MAPPINGS = List.of(
            new PrefixMapping("javax.servlet.", "jakarta.servlet."),
            new PrefixMapping("javax.validation.", "jakarta.validation."),
            new PrefixMapping("javax.ws.rs.", "jakarta.ws.rs."),
            new PrefixMapping("io.dropwizard.Application", "io.dropwizard.core.Application"),
            new PrefixMapping("io.dropwizard.Configuration", "io.dropwizard.core.Configuration"),
            new PrefixMapping("io.dropwizard.setup.", "io.dropwizard.core.setup."),
            new PrefixMapping("com.fasterxml.jackson.jaxrs.", "com.fasterxml.jackson.jakarta.rs."),
            new PrefixMapping("io.dropwizard.logging.AbstractAppenderFactory", "io.dropwizard.logging.common.AbstractAppenderFactory"),
            new PrefixMapping("io.dropwizard.logging.async.", "io.dropwizard.logging.common.async."),
            new PrefixMapping("io.dropwizard.logging.filter.", "io.dropwizard.logging.common.filter."),
            new PrefixMapping("io.dropwizard.logging.layout.", "io.dropwizard.logging.common.layout.")
    );

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length == 2 ? args[1] : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputDir);
        launcher.buildModel();

        rewriteSources(Paths.get(inputDir), Paths.get(outputDir));
    }

    private static String rewrite(String qualifiedName) {
        for (PrefixMapping mapping : MAPPINGS) {
            if (qualifiedName.startsWith(mapping.oldPrefix)) {
                return mapping.newPrefix + qualifiedName.substring(mapping.oldPrefix.length());
            }
        }
        return qualifiedName;
    }

    private record PrefixMapping(String oldPrefix, String newPrefix) {
    }

    private static void rewriteSources(Path inputDir, Path outputDir) {
        try (Stream<Path> paths = Files.walk(inputDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            Path target = inputDir.equals(outputDir) ? path : outputDir.resolve(inputDir.relativize(path));
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            String rewritten = content;
                            for (PrefixMapping mapping : MAPPINGS) {
                                rewritten = rewritten.replace(mapping.oldPrefix, mapping.newPrefix);
                            }
                            if (!rewritten.equals(content)) {
                                Files.createDirectories(target.getParent());
                                Files.writeString(target, rewritten, StandardCharsets.UTF_8);
                            } else if (!inputDir.equals(outputDir)) {
                                Files.createDirectories(target.getParent());
                                Files.writeString(target, content, StandardCharsets.UTF_8);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
