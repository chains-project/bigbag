package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-root> [output-source-root]");
        }

        final Path inputRoot = Paths.get(args[0]);
        final Path outputRoot = args.length > 1
                ? Paths.get(args[1])
                : inputRoot.resolveSibling(inputRoot.getFileName() + "-transformed");

        final Launcher launcher = new Launcher();
        launcher.addInputResource(inputRoot.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(outputRoot.toFile());
        launcher.buildModel();

        final List<CtTypeReference<?>> references = new ArrayList<>(
                launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            if (OLD_TYPE.equals(reference.getQualifiedName())) {
                reference.replace(launcher.getFactory().Type().createReference(NEW_TYPE));
            }
        }

        launcher.getModel().getElements(new TypeFilter<>(spoon.reflect.declaration.CtImport.class)).forEach(ctImport -> {
            final Object imported = ctImport.getReference();
            if (imported instanceof CtTypeReference<?> typeReference
                    && OLD_TYPE.equals(typeReference.getQualifiedName())) {
                ctImport.setReference(launcher.getFactory().Type().createReference(NEW_TYPE));
            }
        });

        launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class)).forEach(compilationUnit -> {
            final List<spoon.reflect.declaration.CtImport> updatedImports = new ArrayList<>(compilationUnit.getImports());
            for (int i = 0; i < updatedImports.size(); i++) {
                final spoon.reflect.declaration.CtImport ctImport = updatedImports.get(i);
                final Object imported = ctImport.getReference();
                if (imported instanceof CtTypeReference<?> typeReference
                        && OLD_TYPE.equals(typeReference.getQualifiedName())) {
                    updatedImports.set(i, launcher.getFactory().createImport(
                            launcher.getFactory().Type().createReference(NEW_TYPE)));
                }
            }
            compilationUnit.setImports(updatedImports);
        });

        launcher.prettyprint();

        try (Stream<Path> paths = Files.walk(outputRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    final String content = Files.readString(path, StandardCharsets.UTF_8)
                            .replace("import " + OLD_TYPE + ";", "import " + NEW_TYPE + ";")
                            .replace(OLD_TYPE, NEW_TYPE);
                    Files.writeString(path, content, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
