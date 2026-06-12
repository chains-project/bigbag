package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/scheduler/safeplace/src/test/java/org/btrplace/safeplace/DSN.java");
        launcher.setSourceOutputDirectory("/workspace/scheduler/safeplace/src/test/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);

        CtModel model = launcher.buildModel();
        System.out.println("Model built. Types found: " + model.getAllTypes().size());
        
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if ("com.github.javaparser.printer.PrettyPrinterConfiguration".equals(ref.getQualifiedName())) {
                System.out.println("Fixing reference: " + ref);
                ref.setPackage(launcher.getFactory().Package().getOrCreate("com.github.javaparser.printer.configuration").getReference());
            }
        }
        
        launcher.prettyprint();
    }
}
