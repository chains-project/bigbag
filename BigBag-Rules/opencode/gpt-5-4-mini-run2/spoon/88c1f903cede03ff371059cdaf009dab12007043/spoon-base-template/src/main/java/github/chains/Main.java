package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final String OLD_ZIPFILE_FQCN = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_ZIPFILE_FQCN = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        final Path inputDirectory = args.length > 0
                ? Paths.get(args[0])
                : Paths.get(".");
        final Path outputDirectory = args.length > 1
                ? Paths.get(args[1])
                : inputDirectory;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(inputDirectory.toString());
        launcher.setSourceOutputDirectory(outputDirectory.toString());
        launcher.buildModel();

        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            final CtReference reference = ctImport.getReference();
            if (reference instanceof CtTypeReference
                    && OLD_ZIPFILE_FQCN.equals(((CtTypeReference<?>) reference).getQualifiedName())) {
                ctImport.delete();
            }
        }

        for (CtTypeReference<?> reference : launcher.getModel().getElements(
                new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
            if (OLD_ZIPFILE_FQCN.equals(reference.getQualifiedName())) {
                final CtTypeReference<?> replacement = launcher.getFactory().Type()
                        .createReference(NEW_ZIPFILE_FQCN);
                reference.replace(replacement);
            }
        }

        launcher.prettyprint();
    }
}
