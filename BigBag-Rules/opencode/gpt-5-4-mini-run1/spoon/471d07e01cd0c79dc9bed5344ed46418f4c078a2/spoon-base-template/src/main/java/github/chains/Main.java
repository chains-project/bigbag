package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_PACKAGE = "com.google.api.services.cloudresourcemanager";
    private static final String NEW_PACKAGE = "com.google.api.services.cloudresourcemanager.v3";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected arguments: <input-source-dir> <output-source-dir>");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        for (Path sourceRoot : sourceRoots(inputDir)) {
            launcher.addInputResource(sourceRoot.toString());
        }
        launcher.setSourceOutputDirectory(outputDir.toString());
        launcher.buildModel();

        launcher.getModel().getElements(new TypeFilter<>(CtImport.class)).forEach(Main::rewriteImport);
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(Main::rewriteTypeReference);
        launcher.getModel().getElements(new TypeFilter<>(CtPackageReference.class)).forEach(Main::rewritePackageReference);

        launcher.prettyprint();
        rewriteGeneratedSources(outputDir);
    }

    private static List<Path> sourceRoots(Path inputDir) {
        List<Path> roots = new ArrayList<>();
        Path mainJava = inputDir.resolve("src/main/java");
        Path testJava = inputDir.resolve("src/test/java");
        if (Files.isDirectory(mainJava) || Files.isDirectory(testJava)) {
            if (Files.isDirectory(mainJava)) {
                roots.add(mainJava);
            }
            if (Files.isDirectory(testJava)) {
                roots.add(testJava);
            }
            return roots;
        }

        roots.add(inputDir);
        return roots;
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference) {
        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName == null
                || qualifiedName.startsWith(NEW_PACKAGE)
                || !qualifiedName.startsWith(OLD_PACKAGE + ".")) {
            return;
        }

        String migratedName = NEW_PACKAGE + qualifiedName.substring(OLD_PACKAGE.length());
        reference.setPackage(reference.getFactory().Package().createReference(NEW_PACKAGE));
        reference.setSimpleName(migratedName.substring(migratedName.lastIndexOf('.') + 1));
    }

    private static void rewriteImport(CtImport ctImport) {
        if (!(ctImport.getReference() instanceof CtTypeReference)) {
            return;
        }

        CtTypeReference<?> reference = (CtTypeReference<?>) ctImport.getReference();
        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(OLD_PACKAGE + ".")) {
            return;
        }

        String migratedName = NEW_PACKAGE + qualifiedName.substring(OLD_PACKAGE.length());
        ctImport.setReference(reference.getFactory().Type().createReference(migratedName));
    }

    private static void rewritePackageReference(CtPackageReference reference) {
        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.equals(OLD_PACKAGE)) {
            return;
        }

        CtPackageReference migratedReference = reference.getFactory().Package().createReference(NEW_PACKAGE);
        reference.setSimpleName(migratedReference.getSimpleName());
    }

    private static void rewriteGeneratedSources(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(
                            path -> {
                                try {
                                    String content = Files.readString(path, StandardCharsets.UTF_8);
                                    String updated = content.replace(OLD_PACKAGE, NEW_PACKAGE);
                                    if (!updated.equals(content)) {
                                        Files.writeString(path, updated, StandardCharsets.UTF_8);
                                    }
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                }
                            });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
