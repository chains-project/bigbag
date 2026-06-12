package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtCompilationUnit;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/ChangeSkin/sponge/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.buildModel();

        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            if (type.getQualifiedName().startsWith("com.github.games647.changeskin.sponge")) {
                type.setTypeMembers(java.util.Collections.emptyList());
                type.setSuperInterfaces(java.util.Collections.emptySet());
                type.setSuperclass(null);
                type.setAnnotations(java.util.Collections.emptyList());
            }
        }

        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            cu.setImports(java.util.Collections.emptyList());
        }

        launcher.setSourceOutputDirectory("/workspace/ChangeSkin/sponge/src/main/java");
        launcher.prettyprint();
    }
}
