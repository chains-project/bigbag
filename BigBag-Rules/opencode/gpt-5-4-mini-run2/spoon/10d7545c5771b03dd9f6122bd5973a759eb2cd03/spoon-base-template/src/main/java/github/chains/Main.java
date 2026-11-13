package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    private static final String OLD_TYPE = "io.dropwizard.setup.Environment";
    private static final String NEW_TYPE = "io.dropwizard.core.setup.Environment";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.buildModel();

        launcher.getModel().getElements(new TypeFilter<>(CtImport.class)).forEach(importElement -> {
            if (OLD_TYPE.equals(importElement.getReference().toString())) {
                importElement.setReference(launcher.getFactory().Type().createReference(NEW_TYPE));
            }
        });

        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(reference -> {
            String mapped = mapQualifiedName(reference.getQualifiedName());
            if (mapped != null) {
                reference.setPackage(launcher.getFactory().Package().createReference(packageName(mapped)));
                reference.setSimpleName(simpleName(mapped));
            }
        });

        launcher.prettyprint();
    }

    private static String mapQualifiedName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        if (OLD_TYPE.equals(qualifiedName)) {
            return NEW_TYPE;
        }
        if (qualifiedName.startsWith("javax.")) {
            return "jakarta." + qualifiedName.substring("javax.".length());
        }
        return null;
    }

    private static String packageName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? "" : qualifiedName.substring(0, lastDot);
    }

    private static String simpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
    }
}
