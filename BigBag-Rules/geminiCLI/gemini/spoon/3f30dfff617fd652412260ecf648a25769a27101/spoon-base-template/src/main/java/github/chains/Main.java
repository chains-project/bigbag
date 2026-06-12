package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/cdi-test/cdi-test-core/src/main/java");
        
        File generatedSources = new File("/workspace/cdi-test/cdi-test-core/target/generated-sources/annotations");
        if (generatedSources.exists()) {
            launcher.addInputResource(generatedSources.getAbsolutePath());
        }
        
        String cp = new String(Files.readAllBytes(Paths.get("/workspace/cdi-test/cdi-test-core/cp.txt"))).trim();
        launcher.getEnvironment().setSourceClasspath(cp.split(":"));
        
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        
        launcher.buildModel();
        CtModel model = launcher.getModel();
        
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (ref.getQualifiedName().startsWith("javax.interceptor.")) {
                ref.setPackage(launcher.getFactory().Package().createReference("jakarta.interceptor"));
            }
        }
        
        launcher.setSourceOutputDirectory("/workspace/cdi-test/cdi-test-core/src/main/java");
        launcher.prettyprint();
    }
}
