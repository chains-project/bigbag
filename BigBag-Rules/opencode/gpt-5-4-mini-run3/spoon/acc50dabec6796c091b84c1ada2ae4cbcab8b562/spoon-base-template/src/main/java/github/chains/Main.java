package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_FQN = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_FQN = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-src-dir> <output-dir>");
            System.exit(1);
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toString());

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> reference) {
                if (OLD_FQN.equals(reference.getQualifiedName())) {
                    reference.setSimpleName("StrutsPrepareAndExecuteFilter");
                    reference.setPackage(reference.getFactory().createPackageReference().setSimpleName("org.apache.struts2.dispatcher"));
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport importRef) {
                if (importRef.getReference() instanceof CtTypeReference<?> typeReference
                        && OLD_FQN.equals(typeReference.getQualifiedName())) {
                    importRef.setReference(typeReference.getFactory().Type().createReference(NEW_FQN));
                }
            }
        });

        launcher.run();

        try {
            Files.walk(output)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            String updated = source.replace(OLD_FQN, NEW_FQN);
                            if (!source.equals(updated)) {
                                Files.writeString(path, updated, StandardCharsets.UTF_8);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
