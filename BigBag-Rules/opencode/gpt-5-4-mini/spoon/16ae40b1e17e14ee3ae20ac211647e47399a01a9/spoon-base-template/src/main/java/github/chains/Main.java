package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;
import java.util.stream.Collectors;
public class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
                () -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));

        CtModel model = launcher.buildModel();
        model.getElements((CtTypeReference<?> candidate) -> OLD_TYPE.equals(candidate.getQualifiedName()))
                .forEach(candidate -> candidate.replace(candidate.getFactory().createReference(NEW_TYPE)));
        model.getElements((CtCompilationUnit candidate) -> candidate.getImports().stream()
                        .anyMatch(ctImport -> ctImport.toString().contains(OLD_TYPE)))
                .forEach(candidate -> {
                    List<CtImport> imports = candidate.getImports().stream()
                            .filter(ctImport -> !ctImport.toString().contains(OLD_TYPE))
                            .collect(Collectors.toList());
                    candidate.getImports().clear();
                    candidate.getImports().addAll(imports);
                });
        launcher.prettyprint();
    }
}
