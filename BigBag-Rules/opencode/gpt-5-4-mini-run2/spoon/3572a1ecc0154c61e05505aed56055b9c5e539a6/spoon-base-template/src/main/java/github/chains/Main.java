package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "org.apache.thrift.transport.TFastFramedTransport";
    private static final String NEW_TYPE = "org.apache.thrift.transport.layered.TFastFramedTransport";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <sourceDir> [outputDir]");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceRoot;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(outputRoot.toString());

        addJavaSources(launcher, sourceRoot);
        launcher.buildModel();

        CtTypeReference<?> replacement = launcher.getFactory().Type().createReference(NEW_TYPE);

        List<CtTypeReference> references = launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference reference : references) {
            if (OLD_TYPE.equals(reference.getQualifiedName())) {
                reference.replace(replacement.clone());
            }
        }

        List<CtCompilationUnit> compilationUnits = launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class));
        for (CtCompilationUnit cu : compilationUnits) {
            List<CtImport> filteredImports = new ArrayList<>();
            for (CtImport ctImport : cu.getImports()) {
                CtReference reference = ctImport.getReference();
                if (reference instanceof CtTypeReference && OLD_TYPE.equals(((CtTypeReference<?>) reference).getQualifiedName())) {
                    continue;
                }
                filteredImports.add(ctImport);
            }
            cu.setImports(filteredImports);
        }

        launcher.prettyprint();
        normalizeLegacyImports(outputRoot);
    }

    private static void addJavaSources(Launcher launcher, Path sourceRoot) {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .collect(Collectors.toList());

            for (Path path : javaFiles) {
                launcher.addInputResource(path.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan Java sources under " + sourceRoot, e);
        }
    }

    private static void normalizeLegacyImports(Path outputRoot) {
        try (Stream<Path> paths = Files.walk(outputRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            String updated = content.lines()
                                    .filter(line -> !line.trim().equals("import " + OLD_TYPE + ";"))
                                    .collect(Collectors.joining(System.lineSeparator()));
                            if (!content.equals(updated)) {
                                Files.writeString(path, updated, StandardCharsets.UTF_8);
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to normalize imports in " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to normalize generated sources under " + outputRoot, e);
        }
    }
}
