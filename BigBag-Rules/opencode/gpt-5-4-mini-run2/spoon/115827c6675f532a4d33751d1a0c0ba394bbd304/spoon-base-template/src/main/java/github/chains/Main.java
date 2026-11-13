package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_PREFIX = "javax.servlet";
    private static final String NEW_PREFIX = "jakarta.servlet";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath();
        Path outputDir = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath()
                : inputDir.resolveSibling(inputDir.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.buildModel();

        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();
        List<CtTypeReference<?>> references = new ArrayList<>(model.getElements(new TypeFilter<>(CtTypeReference.class)));
        List<CtImport> imports = new ArrayList<>(model.getElements(new TypeFilter<>(CtImport.class)));
        List<CtCompilationUnit> compilationUnits = new ArrayList<>(model.getElements(new TypeFilter<>(CtCompilationUnit.class)));

        for (CtTypeReference<?> reference : references) {
            String qualifiedName = reference.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX + ".")) {
                CtTypeReference<?> replacement = factory.Type().createReference(
                        NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length()));
                reference.replace(replacement);
            }
        }

        for (CtImport ctImport : imports) {
            CtReference imported = ctImport.getReference();
            if (imported instanceof CtTypeReference) {
                String qualifiedName = ((CtTypeReference<?>) imported).getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX + ".")) {
                    ctImport.setReference(factory.Type().createReference(
                            NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length())));
                }
            }
        }

        for (CtCompilationUnit compilationUnit : compilationUnits) {
            compilationUnit.setImports(List.of());
        }

        launcher.prettyprint();
        normalizeServletNamespace(outputDir);
    }

    private static void normalizeServletNamespace(Path outputDir) {
        try {
            Files.walk(outputDir)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            String updated = content.replace("javax.servlet", NEW_PREFIX);
                            if (!content.equals(updated)) {
                                Files.writeString(path, updated, StandardCharsets.UTF_8);
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
