package github.chains;

import java.io.File;
import spoon.Launcher;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_TYPE = "org.codehaus.plexus.configuration.PlexusConfiguration";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected input source directory as the first argument");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        replaceTypeReferences(launcher);
        launcher.prettyprint();
    }

    private static void replaceTypeReferences(Launcher launcher) {
        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(reference.getQualifiedName())) {
                CtTypeReference<?> replacement = launcher.getFactory().Type().createReference(NEW_TYPE);
                reference.replace(replacement);
            }
        }

        for (CtTypeAccess<?> access : launcher.getModel().getElements(new TypeFilter<>(CtTypeAccess.class))) {
            if (access.getAccessedType() != null && OLD_TYPE.equals(access.getAccessedType().getQualifiedName())) {
                access.setType(launcher.getFactory().Type().createReference(NEW_TYPE));
            }
        }
    }
}
