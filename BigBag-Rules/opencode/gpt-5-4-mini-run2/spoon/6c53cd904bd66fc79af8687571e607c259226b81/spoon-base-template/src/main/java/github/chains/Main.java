package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.compiler.FileSystemFolder;

public class Main {
    private static final String OLD_TYPE = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_TYPE = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected <input-source-dir> <output-source-dir>");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addInputResource(new FileSystemFolder(input.toFile()));
        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public boolean isToBeProcessed(CtTypeReference<?> candidate) {
                return candidate != null && OLD_TYPE.equals(candidate.getQualifiedName());
            }

            @Override
            public void process(CtTypeReference<?> candidate) {
                candidate.replace(getFactory().Type().createReference(NEW_TYPE));
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public boolean isToBeProcessed(CtImport candidate) {
                return candidate != null
                        && candidate.getReference() != null
                        && candidate.getReference().toString().equals(OLD_TYPE);
            }

            @Override
            public void process(CtImport candidate) {
                candidate.setReference(getFactory().Type().createReference(NEW_TYPE));
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtCompilationUnit>() {
            @Override
            public boolean isToBeProcessed(CtCompilationUnit candidate) {
                return candidate != null;
            }

            @Override
            public void process(CtCompilationUnit candidate) {
                List<CtImport> filtered = new ArrayList<>();
                for (CtImport importEntry : candidate.getImports()) {
                    if (importEntry == null
                            || importEntry.getReference() == null
                            || !OLD_TYPE.equals(importEntry.getReference().toString())) {
                        filtered.add(importEntry);
                    }
                }
                candidate.setImports(filtered);
            }
        });

        launcher.run();
        try {
            Files.walk(output)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    String source = Files.readString(path, StandardCharsets.UTF_8);
                                    String updated = source.replace(OLD_TYPE, NEW_TYPE);
                                    if (!source.equals(updated)) {
                                        Files.writeString(path, updated, StandardCharsets.UTF_8);
                                    }
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
