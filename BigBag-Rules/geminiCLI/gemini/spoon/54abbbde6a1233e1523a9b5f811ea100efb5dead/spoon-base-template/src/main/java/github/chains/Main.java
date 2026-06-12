package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Add input resources
        launcher.addInputResource("../jcabi-ssh/src/main/java/com/jcabi/ssh/Ssh.java");
        launcher.addInputResource("../jcabi-ssh/src/main/java/com/jcabi/ssh/SshByPassword.java");
        
        // Build model
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();

        // Find all field reads of Tv
        List<CtFieldRead<?>> fieldReads = launcher.getModel().getElements(new TypeFilter<>(CtFieldRead.class));
        
        for (CtFieldRead<?> fieldRead : fieldReads) {
            String targetName = fieldRead.getVariable().getSimpleName();
            String targetType = fieldRead.getTarget() != null ? fieldRead.getTarget().toString() : "";
            
            if (targetType.endsWith("Tv")) {
                 if ("SEVEN".equals(targetName)) {
                     fieldRead.replace(factory.Code().createLiteral(7));
                 } else if ("MILLION".equals(targetName)) {
                     fieldRead.replace(factory.Code().createLiteral(1000000));
                 } else if ("TEN".equals(targetName)) {
                     fieldRead.replace(factory.Code().createLiteral(10));
                 }
            }
        }

        // Remove imports
        for (spoon.reflect.declaration.CtCompilationUnit cu : factory.CompilationUnit().getMap().values()) {
            cu.getImports().removeIf(i -> i.toString().contains("com.jcabi.aspects.Tv"));
        }

        // Set output directory to overwrite original files
        launcher.setSourceOutputDirectory("../jcabi-ssh/src/main/java");
        
        // Pretty print (save)
        launcher.prettyprint();
    }
}