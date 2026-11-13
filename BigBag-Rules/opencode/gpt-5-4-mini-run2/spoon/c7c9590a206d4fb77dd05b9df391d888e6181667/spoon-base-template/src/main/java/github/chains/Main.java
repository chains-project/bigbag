package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_RENDERING_CONTEXT = "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_RENDERING_CONTEXT = "org.apache.maven.doxia.siterenderer.RenderingContext";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        CtModel model = launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtTypeReference<?> newRenderingContextRef = factory.Type().createReference(NEW_RENDERING_CONTEXT);

        replaceTypeReference(model, newRenderingContextRef);

        launcher.prettyprint();
    }

    private static void replaceTypeReference(CtModel model, CtTypeReference<?> replacement) {
        for (CtTypeReference<?> reference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_RENDERING_CONTEXT.equals(reference.getQualifiedName())) {
                reference.replace(replacement.clone());
            }
        }

        for (CtImport imp : model.getElements(new TypeFilter<>(CtImport.class))) {
            CtReference ref = imp.getReference();
            if (ref instanceof CtTypeReference && OLD_RENDERING_CONTEXT.equals(((CtTypeReference<?>) ref).getQualifiedName())) {
                imp.delete();
            }
        }
    }
}
