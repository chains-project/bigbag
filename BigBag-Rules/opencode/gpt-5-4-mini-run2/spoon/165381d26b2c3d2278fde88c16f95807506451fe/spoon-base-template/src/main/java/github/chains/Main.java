package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_PRETTY_PRINTER_CONFIGURATION = "com.github.javaparser.printer.PrettyPrinterConfiguration";
    private static final String NEW_PRETTY_PRINTER_CONFIGURATION = "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(args[0]);

        if (args.length > 1) {
            launcher.setSourceOutputDirectory(new File(args[1]));
        } else {
            launcher.setSourceOutputDirectory(new File(args[0] + "-fixed"));
        }

        launcher.buildModel();

        List<CtTypeReference<?>> references = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            String qualifiedName = reference.getQualifiedName();
            if (OLD_PRETTY_PRINTER_CONFIGURATION.equals(qualifiedName)
                    || reference.toString().contains("com.github.javaparser.printer.PrettyPrinterConfiguration")) {
                reference.replace(reference.getFactory().Type().createReference(NEW_PRETTY_PRINTER_CONFIGURATION));
            }
        }

        List<CtImport> imports = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtImport.class)));
        for (CtImport ctImport : imports) {
            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> reference = (CtTypeReference<?>) ctImport.getReference();
                String qualifiedName = reference.getQualifiedName();
                if (OLD_PRETTY_PRINTER_CONFIGURATION.equals(qualifiedName)
                        || reference.toString().contains("com.github.javaparser.printer.PrettyPrinterConfiguration")) {
                    ctImport.setReference(reference.getFactory().Type().createReference(NEW_PRETTY_PRINTER_CONFIGURATION));
                }
            }
        }

        launcher.prettyprint();
    }
}
