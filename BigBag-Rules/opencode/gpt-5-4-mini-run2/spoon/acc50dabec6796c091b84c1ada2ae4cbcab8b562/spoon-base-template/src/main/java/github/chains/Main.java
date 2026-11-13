package github.chains;

import java.util.Objects;
import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_TYPE = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        CtTypeReference<?> replacement = launcher.getFactory().Type().createReference(NEW_TYPE);
        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            if (type.getPosition() != null && type.getPosition().getCompilationUnit() != null) {
                CtCompilationUnit compilationUnit = type.getPosition().getCompilationUnit();
                compilationUnit.setImports(
                        compilationUnit.getImports().stream()
                                .filter(ctImport -> {
                                    if (ctImport.getReference() instanceof CtTypeReference) {
                                        CtTypeReference<?> importedType =
                                                (CtTypeReference<?>) ctImport.getReference();
                                        return !Objects.equals(importedType.getQualifiedName(), OLD_TYPE);
                                    }
                                    return true;
                                })
                                .collect(java.util.stream.Collectors.toList()));
                for (spoon.reflect.declaration.CtImport ctImport : compilationUnit.getImports()) {
                    if (ctImport.getReference() instanceof CtTypeReference) {
                        CtTypeReference<?> importedType = (CtTypeReference<?>) ctImport.getReference();
                        if (Objects.equals(importedType.getQualifiedName(), OLD_TYPE)) {
                            ctImport.setReference(replacement.clone());
                        }
                    }
                }
            }
            for (CtTypeReference<?> reference : type.getElements(new TypeFilter<>(CtTypeReference.class))) {
                if (Objects.equals(reference.getQualifiedName(), OLD_TYPE)) {
                    reference.replace(replacement.clone());
                }
            }
        }

        launcher.prettyprint();
    }
}
