package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/plexus-archiver/src/test/java/org/codehaus/plexus/archiver/DuplicateFilesTest.java");
        launcher.setSourceOutputDirectory("/workspace/plexus-archiver/src/test/java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        for (CtInvocation<?> inv : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (inv.getExecutable().getSimpleName().equals("setThreshold") &&
                inv.getTarget() != null &&
                inv.getTarget() instanceof CtInvocation) {
                CtInvocation<?> target = (CtInvocation<?>) inv.getTarget();
                if (target.getExecutable().getSimpleName().equals("getLoggerManager")) {
                    inv.delete();
                }
            }
        }

        launcher.prettyprint();
    }
}
