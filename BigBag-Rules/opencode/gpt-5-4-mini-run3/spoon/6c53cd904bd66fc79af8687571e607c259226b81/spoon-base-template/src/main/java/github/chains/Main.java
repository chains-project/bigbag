package github.chains;

import java.io.File;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_TYPE = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_TYPE = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <inputDir> <outputDir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addInputResource(args[0]);
        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> reference) {
                if (OLD_TYPE.equals(reference.getQualifiedName())) {
                    reference.replace(reference.getFactory().Type().createReference(NEW_TYPE));
                }
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport imported) {
                if (imported.getReference() != null
                    && OLD_TYPE.equals(imported.getReference().getQualifiedName())) {
                    imported.setReference(imported.getFactory().Type().createReference(NEW_TYPE));
                }
            }
        });
        launcher.run();
    }
}
