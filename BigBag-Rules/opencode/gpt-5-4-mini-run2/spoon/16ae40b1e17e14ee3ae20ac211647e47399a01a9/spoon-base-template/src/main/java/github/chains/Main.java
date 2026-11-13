package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <input-source-dir> [output-dir]");
        }

        final Path inputDir = Paths.get(args[0]);
        final Path outputDir = args.length > 1
                ? Paths.get(args[1])
                : inputDir.resolveSibling(inputDir.getFileName() + "-rewritten");

        Files.createDirectories(outputDir);

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        final CtTypeReference<?> newType = launcher.getFactory().Type().createReference(NEW_TYPE);

        for (CtTypeReference<?> typeReference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(typeReference.getQualifiedName())) {
                typeReference.replace(newType.clone());
            }
        }

        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (ctImport.getReference() instanceof CtTypeReference) {
                final CtTypeReference<?> importType = (CtTypeReference<?>) ctImport.getReference();
                if (OLD_TYPE.equals(importType.getQualifiedName())) {
                    ctImport.replace(launcher.getFactory().Type().createImport(newType.clone()));
                }
            }
        }

        launcher.prettyprint();
        normalizeImports(outputDir);
    }

    private static void normalizeImports(final Path outputDir) throws Exception {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            content = content.replace("import " + OLD_TYPE + ";\n", "");
                            content = content.replace(OLD_TYPE, NEW_TYPE);
                            Files.writeString(path, content, StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
