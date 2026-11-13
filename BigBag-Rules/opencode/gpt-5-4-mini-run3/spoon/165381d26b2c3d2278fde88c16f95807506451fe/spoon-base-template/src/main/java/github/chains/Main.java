package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_TYPE = "com.github.javaparser.printer.PrettyPrinterConfiguration";
    private static final String NEW_TYPE = "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toString());

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public boolean isToBeProcessed(CtTypeReference<?> candidate) {
                return candidate != null && OLD_TYPE.equals(candidate.getQualifiedName());
            }

            @Override
            public void process(CtTypeReference<?> reference) {
                reference.replace(launcher.getFactory().Type().createReference(NEW_TYPE));
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public boolean isToBeProcessed(CtImport candidate) {
                CtReference reference = candidate.getReference();
                return reference instanceof CtTypeReference<?> typeReference
                        && OLD_TYPE.equals(typeReference.getQualifiedName());
            }

            @Override
            public void process(CtImport ctImport) {
                ctImport.setReference(launcher.getFactory().Type().createReference(NEW_TYPE));
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtCompilationUnit>() {
            @Override
            public boolean isToBeProcessed(CtCompilationUnit unit) {
                return unit != null && unit.getImports().stream().anyMatch(ctImport -> {
                    CtReference reference = ctImport.getReference();
                    return reference instanceof CtTypeReference<?> typeReference
                            && OLD_TYPE.equals(typeReference.getQualifiedName());
                });
            }

            @Override
            public void process(CtCompilationUnit unit) {
                for (CtImport ctImport : unit.getImports()) {
                    CtReference reference = ctImport.getReference();
                    if (reference instanceof CtTypeReference<?> typeReference
                            && OLD_TYPE.equals(typeReference.getQualifiedName())) {
                        ctImport.setReference(launcher.getFactory().Type().createReference(NEW_TYPE));
                    }
                }
            }
        });

        launcher.run();
    }
}
