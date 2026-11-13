package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_TYPE = "org.htmlunit.ScriptResult";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected <input-source-dir> <output-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));

        CtModel model = launcher.buildModel();
        CtTypeReference<?> newType = launcher.getFactory().Type().createReference(NEW_TYPE);

        for (CtTypeReference<?> typeReference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(typeReference.getQualifiedName())) {
                typeReference.replace(newType.clone());
            }
        }

        launcher.prettyprint();
    }
}
