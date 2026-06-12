package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/junit-quickcheck/core/src/test/java/com/pholser/junit/quickcheck/internal/generator/RegisterGeneratorsByConventionTest.java");
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        for (CtTypeReference<?> ref : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if ("org.mockito.runners.MockitoJUnitRunner".equals(ref.getQualifiedName())) {
                ref.setPackage(launcher.getFactory().Package().getOrCreate("org.mockito.junit").getReference());
            }
        }

        for (spoon.reflect.declaration.CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            java.util.List<CtImport> newImports = new java.util.ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.getReference() instanceof spoon.reflect.reference.CtTypeReference) {
                    spoon.reflect.reference.CtTypeReference<?> ref = (spoon.reflect.reference.CtTypeReference<?>) imp.getReference();
                    if ("org.mockito.runners.MockitoJUnitRunner".equals(ref.getQualifiedName())) {
                        imp.setReference(launcher.getFactory().Type().createReference("org.mockito.junit.MockitoJUnitRunner"));
                    }
                }
                newImports.add(imp);
            }
            cu.setImports(newImports);
        }

        launcher.setSourceOutputDirectory("/workspace/junit-quickcheck/core/src/test/java/");
        launcher.prettyprint();
    }
}
