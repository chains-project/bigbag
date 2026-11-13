package github.chains;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import spoon.Launcher;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final Map<String, String> RENAMES = new HashMap<>();

    static {
        RENAMES.put("org.junit.Assert", "org.junit.jupiter.api.Assertions");
        RENAMES.put("org.junit.Assume", "org.junit.jupiter.api.Assumptions");
        RENAMES.put("org.junit.Ignore", "org.junit.jupiter.api.Disabled");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir] [source-classpath]");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(args.length < 3);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(args[0]);
        if (args.length >= 3 && !args[2].isBlank()) {
            launcher.getEnvironment().setSourceClasspath(args[2].split(File.pathSeparator));
        }
        launcher.buildModel();

        launcher.getModel().getElements(new TypeFilter<>(CtImport.class)).forEach(Main::rewriteImport);
        launcher.getModel().getElements(new TypeFilter<>(CtAnnotation.class)).forEach(Main::rewriteAnnotation);

        launcher.setSourceOutputDirectory(new File(args.length >= 2 ? args[1] : args[0]));
        launcher.prettyprint();
    }

    private static void rewriteImport(CtImport ctImport) {
        CtReference reference = ctImport.getReference();
        if (reference instanceof CtTypeReference) {
            rewriteTypeReference((CtTypeReference<?>) reference, ctImport);
        } else if (reference instanceof CtExecutableReference) {
            rewriteExecutableReference((CtExecutableReference<?>) reference, ctImport);
        }
    }

    private static void rewriteAnnotation(CtAnnotation<?> annotation) {
        CtTypeReference<? extends java.lang.annotation.Annotation> type = annotation.getAnnotationType();
        if (type != null) {
            rewriteTypeReference(type, annotation);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rewriteTypeReference(CtTypeReference<?> reference, CtImport ctImport) {
        String renamed = RENAMES.get(reference.getQualifiedName());
        if (renamed != null) {
            ctImport.setReference((CtReference) reference.getFactory().Type().createReference(renamed));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rewriteTypeReference(CtTypeReference<?> reference, CtAnnotation<?> annotation) {
        String renamed = RENAMES.get(reference.getQualifiedName());
        if (renamed != null) {
            annotation.setAnnotationType((CtTypeReference) reference.getFactory().Type().createReference(renamed));
        }
    }

    private static void rewriteExecutableReference(CtExecutableReference<?> reference, CtImport ctImport) {
        CtTypeReference<?> declaringType = reference.getDeclaringType();
        if (declaringType == null) {
            return;
        }
        String renamed = RENAMES.get(declaringType.getQualifiedName());
        if (renamed != null) {
            reference.setDeclaringType(reference.getFactory().Type().createReference(renamed));
            ctImport.setReference(reference);
        }
    }
}
