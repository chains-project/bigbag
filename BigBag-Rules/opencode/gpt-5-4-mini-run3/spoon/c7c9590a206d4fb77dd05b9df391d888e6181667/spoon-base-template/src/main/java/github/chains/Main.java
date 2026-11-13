package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_TYPE = "org.apache.maven.doxia.siterenderer.RenderingContext";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory path");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8);

        launcher.buildModel();

        CtTypeReference<?> replacement = launcher.getFactory().Type().createReference(NEW_TYPE);
        for (CtType<?> type : launcher.getModel().getElements(new TypeFilter<>(CtType.class))) {
            if (type.getPosition() != null && type.getPosition().getCompilationUnit() != null) {
                type.getPosition().getCompilationUnit().getImports().removeIf(ctImport ->
                        ctImport.getReference() instanceof CtTypeReference<?>
                                && OLD_TYPE.equals(((CtTypeReference<?>) ctImport.getReference()).getQualifiedName()));
            }
        }

        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(reference.getQualifiedName())) {
                reference.replace(replacement.clone());
            }
        }

        launcher.prettyprint();
    }
}
