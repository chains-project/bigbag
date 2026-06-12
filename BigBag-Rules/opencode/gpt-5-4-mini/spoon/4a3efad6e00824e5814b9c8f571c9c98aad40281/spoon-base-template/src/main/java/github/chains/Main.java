package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;

public class Main {

    private static final String OLD_TYPE = "eu.europa.esig.dss.pades.CertificationPermission";
    private static final String NEW_TYPE = "eu.europa.esig.dss.enumerations.CertificationPermission";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Path.of(args[0]);
        Path outputDir = args.length >= 2 ? Path.of(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-spoon-out");
        String[] sourceClasspath = args.length >= 3 && !args[2].isBlank() ? args[2].split(java.io.File.pathSeparator) : new String[0];

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        if (sourceClasspath.length > 0) {
            launcher.getEnvironment().setSourceClasspath(sourceClasspath);
        }
        launcher.setSourceOutputDirectory(outputDir.toFile());

        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public boolean isToBeProcessed(CtImport candidate) {
                return candidate != null
                        && candidate.getReference() instanceof CtTypeReference
                        && OLD_TYPE.equals(((CtTypeReference<?>) candidate.getReference()).getQualifiedName());
            }

            @Override
            public void process(CtImport ctImport) {
                ctImport.setReference(ctImport.getFactory().Type().createReference(NEW_TYPE));
            }
        });

        launcher.run();
    }
}
