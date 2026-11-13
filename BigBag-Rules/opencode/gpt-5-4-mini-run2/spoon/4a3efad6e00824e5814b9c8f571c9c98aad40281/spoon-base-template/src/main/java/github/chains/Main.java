package github.chains;

import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final String OLD_TYPE = "eu.europa.esig.dss.pades.CertificationPermission";
    private static final String NEW_TYPE = "eu.europa.esig.dss.enumerations.CertificationPermission";
    private static final String NEW_SIGNATURE = "eu.europa.esig.dss.pades.PAdESSignatureParameters#setPermission(eu.europa.esig.dss.enumerations.CertificationPermission)";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-src-dir> <output-src-dir>");
        }

        Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path projectRoot = inputRoot;
        while (projectRoot != null && !Files.exists(projectRoot.resolve("pom.xml"))) {
            projectRoot = projectRoot.getParent();
        }
        if (projectRoot == null) {
            throw new IllegalArgumentException("Could not locate pom.xml from input path: " + inputRoot);
        }

        Launcher launcher = new MavenLauncher(projectRoot.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();

        launcher.getModel().getElements((CtTypeReference<?> reference) -> isOldTypeReference(reference)).forEach(Main::rewriteTypeReference);

        launcher.prettyprint();
    }

    private static boolean isOldTypeReference(CtTypeReference<?> reference) {
        return reference != null && OLD_TYPE.equals(reference.getQualifiedName());
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference) {
        CtElement parent = reference.getParent();
        if (parent == null) {
            return;
        }

        CtTypeReference<?> replacement = reference.getFactory().Type().createReference(NEW_TYPE);
        reference.replace(replacement);
    }
}
