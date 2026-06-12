package github.chains;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java");
        
        Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(env));
        
        CtModel model = launcher.buildModel();
        
        List<CtTypeReference> references = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference ref : references) {
            if ("net.lingala.zip4j.core.ZipFile".equals(ref.getQualifiedName())) {
                ref.setPackage(launcher.getFactory().Package().getOrCreate("net.lingala.zip4j").getReference());
            }
        }
        
        for (CtImport imp : model.getElements(new TypeFilter<>(CtImport.class))) {
            if (imp.getReference() instanceof CtTypeReference) {
                CtTypeReference ref = (CtTypeReference) imp.getReference();
                if ("net.lingala.zip4j.core.ZipFile".equals(ref.getQualifiedName())) {
                    ref.setPackage(launcher.getFactory().Package().getOrCreate("net.lingala.zip4j").getReference());
                }
            }
        }

        launcher.setSourceOutputDirectory("/workspace/allure-maven/src/main/java");
        launcher.prettyprint();
    }
}
