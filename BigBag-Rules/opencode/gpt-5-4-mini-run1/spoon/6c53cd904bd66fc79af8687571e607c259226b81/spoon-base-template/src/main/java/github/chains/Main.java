package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_TYPE = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(17);
        addJavaSources(launcher, sourceDir);

        Path tempOutput;
        try {
            tempOutput = Files.createTempDirectory("spoon-output");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        launcher.setSourceOutputDirectory(tempOutput.toFile());

        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();

        for (CtTypeReference<?> typeReference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(typeReference.getQualifiedName())) {
                typeReference.replace(factory.Type().createReference(NEW_TYPE));
            }
        }

        for (CtImport importRef : model.getElements(new TypeFilter<>(CtImport.class))) {
            CtTypeReference<?> importedType = (CtTypeReference<?>) importRef.getReference();
            if (importedType != null && OLD_TYPE.equals(importedType.getQualifiedName())) {
                importRef.setReference(factory.Type().createReference(NEW_TYPE));
            }
        }

        launcher.prettyprint();
        copyGeneratedSources(tempOutput, outputDir);
    }

    private static void addJavaSources(Launcher launcher, Path sourceDir) {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyGeneratedSources(Path generatedDir, Path outputDir) {
        try (Stream<Path> paths = Files.walk(generatedDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(
                            path -> {
                                Path target = outputDir.resolve(generatedDir.relativize(path).toString());
                                try {
                                    Files.createDirectories(target.getParent());
                                    Files.copy(path, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
