package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import spoon.Launcher;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE =
            "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_TYPE =
            "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputDir.resolveSibling(inputDir.getFileName() + "-spooned");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        CtTypeReference<?> newTypeReference = launcher.getFactory().Type().createReference(NEW_TYPE);
        for (CtCompilationUnit compilationUnit : launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class))) {
            List<CtImport> updatedImports = new ArrayList<>();
            boolean changed = false;
            for (CtImport ctImport : compilationUnit.getImports()) {
                if (ctImport.getReference() instanceof CtTypeReference
                        && OLD_TYPE.equals(((CtTypeReference<?>) ctImport.getReference()).getQualifiedName())) {
                    updatedImports.add(launcher.getFactory().createImport(newTypeReference.clone()));
                    changed = true;
                } else {
                    updatedImports.add(ctImport);
                }
            }
            if (changed) {
                compilationUnit.setImports(updatedImports);
            }
        }
        for (CtElement element : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            CtTypeReference<?> typeReference = (CtTypeReference<?>) element;
            if (OLD_TYPE.equals(typeReference.getQualifiedName())) {
                typeReference.replace(newTypeReference.clone());
            }
        }

        launcher.prettyprint();
        removeConflictingImports(outputDir);
    }

    private static void removeConflictingImports(Path outputDir) {
        try {
            Files.walk(outputDir)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::stripConflictingImports);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void stripConflictingImports(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> filtered = new ArrayList<>();
            for (String line : lines) {
                if (line.equals("import " + OLD_TYPE + ";") || line.equals("import " + NEW_TYPE + ";")) {
                    continue;
                }
                filtered.add(line);
            }
            Files.write(path, filtered, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
