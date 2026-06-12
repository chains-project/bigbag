package github.chains;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.experimental.CtUnresolvedImport;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java");
        
        Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(env));
        
        launcher.buildModel();
        
        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> newImports = new ArrayList<>();
            boolean changed = false;
            for (CtImport ctImport : cu.getImports()) {
                if (ctImport instanceof CtUnresolvedImport) {
                    CtUnresolvedImport unres = (CtUnresolvedImport) ctImport;
                    if ("net.lingala.zip4j.core.ZipFile".equals(unres.getUnresolvedReference())) {
                        // Create a new unresolved import
                        CtImport newImport = launcher.getFactory().createUnresolvedImport("net.lingala.zip4j.ZipFile", false);
                        newImports.add(newImport);
                        changed = true;
                    } else {
                        newImports.add(ctImport);
                    }
                } else {
                    newImports.add(ctImport);
                }
            }
            if (changed) {
                cu.setImports(newImports);
            }
        }
        
        launcher.setSourceOutputDirectory("/workspace/allure-maven/src/main/java");
        launcher.prettyprint();
    }
}
