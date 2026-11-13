package github.chains;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String OLD_RENDERING_CONTEXT =
            "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_RENDERING_CONTEXT =
            "org.apache.maven.doxia.siterenderer.RenderingContext";
    private static final String OLD_IMPORT = "import " + OLD_RENDERING_CONTEXT + ";";

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> reference) {
                if (reference != null && OLD_RENDERING_CONTEXT.equals(reference.getQualifiedName())) {
                    reference.replace(reference.getFactory().Type().createReference(NEW_RENDERING_CONTEXT));
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> constructorCall) {
                CtTypeReference<?> type = constructorCall.getType();
                if (type != null && OLD_RENDERING_CONTEXT.equals(type.getQualifiedName())) {
                    constructorCall.setType(constructorCall.getFactory().Type().createReference(NEW_RENDERING_CONTEXT));
                }
            }
        });

        launcher.run();
        removeOldImports(outputDir);
    }

    private static void removeOldImports(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    content = content.replace(OLD_IMPORT + "\r\n", "");
                    content = content.replace(OLD_IMPORT + "\n", "");
                    content = content.replace(OLD_IMPORT, "");
                    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to post-process generated sources", e);
        }
    }
}
